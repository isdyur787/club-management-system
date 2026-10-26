import pymysql
import logging
from typing import List, Dict, Optional
from dbutils.pooled_db import PooledDB
from config import MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE

logger = logging.getLogger(__name__)

# 全局连接池（Singleton）— DBUtils.PooledDB 内部线程安全
_pool = PooledDB(
    creator=pymysql,
    maxcached=5,          # 初始空闲连接数
    maxconnections=15,    # 最大连接数（含使用中）
    blocking=True,        # 池满时阻塞等待
    ping=1,               # 每次获取连接时 ping 检查可用性
    host=MYSQL_HOST, port=MYSQL_PORT,
    user=MYSQL_USER, password=MYSQL_PASSWORD,
    database=MYSQL_DATABASE, charset="utf8mb4",
    cursorclass=pymysql.cursors.DictCursor
)
logger.info(f"PooledDB initialized: maxcached=5, maxconnections=15")


class DBReader:
    """
    数据库读取器 — 通过 DBUtils.PooledDB 管理 MySQL 连接

    设计模式：Object Pool — DBUtils 自动管理连接复用、探活、线程安全

    使用方式：
        # 推荐：上下文管理器（自动归还连接）
        with DBReader() as reader:
            result = reader.search_activities(...)

        # 也可以手动管理
        reader = DBReader()
        try:
            result = reader.search_activities(...)
        finally:
            reader.close()
    """

    def __init__(self):
        self.conn = _pool.connection()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
        return False  # 不抑制异常

    def close(self):
        """归还连接到池（DBUtils 包装的 close 即归还）"""
        if self.conn:
            self.conn.close()
            self.conn = None

    def get_checkin_records(self, activity_id: int) -> List[Dict]:
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("""
                SELECT cr.*, u.real_name, u.email
                FROM act_checkin_record cr
                JOIN sys_user u ON cr.user_id = u.id
                WHERE cr.activity_id = %s AND cr.deleted = 0
                ORDER BY cr.created_at
            """, (activity_id,))
            return cursor.fetchall()

    def get_activity(self, activity_id: int) -> Dict:
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("""
                SELECT a.*, c.club_name
                FROM act_activity a
                JOIN club_info c ON a.club_id = c.id
                WHERE a.id = %s AND a.deleted = 0
            """, (activity_id,))
            return cursor.fetchone()

    def get_activity_reports(self, club_id: int, limit: int = 10) -> List[Dict]:
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("""
                SELECT * FROM ai_activity_report
                WHERE club_id = %s AND status = 'CONFIRMED' AND deleted = 0
                ORDER BY created_at DESC LIMIT %s
            """, (club_id, limit))
            return cursor.fetchall()

    def get_compilation_by_activity(self, activity_id: int) -> Optional[Dict]:
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("""
                SELECT cc.*, a.title as activity_title
                FROM act_checkin_compilation cc
                JOIN act_activity a ON cc.activity_id = a.id
                WHERE cc.activity_id = %s AND cc.deleted = 0
            """, (activity_id,))
            return cursor.fetchone()

    def get_checkin_records_for_compilation(self, activity_id: int) -> List[Dict]:
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("""
                SELECT cr.id, cr.rating, cr.reflection, cr.problem, cr.suggestion,
                       cr.photo_urls, cr.image_descriptions, cr.file_urls,
                       u.real_name, u.email
                FROM act_checkin_record cr
                JOIN sys_user u ON cr.user_id = u.id
                WHERE cr.activity_id = %s AND cr.deleted = 0
                ORDER BY cr.created_at
            """, (activity_id,))
            return cursor.fetchall()

    # ========== Function Calling 工具方法 ==========

    # 中文状态映射
    _STATUS_MAP = {
        "草稿": "DRAFT", "待审批": "APPROVED", "进行中": "IN_PROGRESS",
        "已完成": "COMPLETED", "已归档": "ARCHIVED",
        "正在": "IN_PROGRESS", "进行": "IN_PROGRESS",
        "ongoing": "IN_PROGRESS", "in progress": "IN_PROGRESS", "in_progress": "IN_PROGRESS",
        "draft": "DRAFT", "approved": "APPROVED", "completed": "COMPLETED", "archived": "ARCHIVED",
    }

    def search_activities(self, club_id: int, status: str = None, keyword: str = None) -> Dict:
        """搜索社团活动列表"""
        # 中文/小写状态名转换为标准英文
        if status:
            status = self._STATUS_MAP.get(status, self._STATUS_MAP.get(status.lower(), status.upper()))
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            sql = "SELECT id, title, type, status, location, start_time, end_time, max_participants, current_participants FROM act_activity WHERE club_id = %s AND deleted = 0"
            params = [club_id]
            if status:
                sql += " AND status = %s"
                params.append(status)
            if keyword:
                sql += " AND (title LIKE %s OR description LIKE %s)"
                params.extend([f"%{keyword}%", f"%{keyword}%"])
            sql += " ORDER BY start_time DESC LIMIT 10"
            cursor.execute(sql, params)
            activities = cursor.fetchall()
            return {
                "club_id": club_id,
                "count": len(activities),
                "activities": [{k: str(v) if v is not None else "" for k, v in a.items()} for a in activities]
            }

    def get_club_detail(self, club_id: int) -> Dict:
        """获取社团详细信息"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("""
                SELECT c.id, c.club_name, c.description, c.category,
                       c.president_id, c.member_count, c.approval_status, c.created_at,
                       u.real_name as president_name
                FROM club_info c
                LEFT JOIN sys_user u ON c.president_id = u.id
                WHERE c.id = %s AND c.deleted = 0
            """, (club_id,))
            club = cursor.fetchone()
            if not club:
                return {"error": f"社团 {club_id} 不存在"}

            # 统计活跃成员数
            cursor.execute("""
                SELECT COUNT(*) as active_members FROM club_member
                WHERE club_id = %s AND status = 1 AND deleted = 0
            """, (club_id,))
            active = cursor.fetchone()["active_members"]
            club["active_member_count"] = active
            return {k: str(v) if v is not None else "" for k, v in club.items()}

    def get_activity_stats(self, activity_id: int) -> Dict:
        """获取活动参与统计"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            # 活动基本信息
            cursor.execute("""
                SELECT title, max_participants, current_participants, status
                FROM act_activity WHERE id = %s AND deleted = 0
            """, (activity_id,))
            activity = cursor.fetchone()
            if not activity:
                return {"error": f"活动 {activity_id} 不存在"}

            # 报名人数
            cursor.execute("""
                SELECT COUNT(*) as registration_count FROM reg_registration
                WHERE activity_id = %s AND deleted = 0
            """, (activity_id,))
            reg_count = cursor.fetchone()["registration_count"]

            # 签到人数
            cursor.execute("""
                SELECT COUNT(*) as signin_count FROM reg_sign_in
                WHERE activity_id = %s AND deleted = 0
            """, (activity_id,))
            signin_count = cursor.fetchone()["signin_count"]

            # 打卡统计
            cursor.execute("""
                SELECT COUNT(*) as checkin_count, AVG(rating) as avg_rating
                FROM act_checkin_record
                WHERE activity_id = %s AND deleted = 0 AND rating IS NOT NULL
            """, (activity_id,))
            checkin_stats = cursor.fetchone()

            return {
                "activity_id": activity_id,
                "title": activity["title"],
                "status": activity["status"],
                "max_participants": activity["max_participants"],
                "current_participants": activity["current_participants"],
                "registration_count": reg_count,
                "signin_count": signin_count,
                "checkin_count": checkin_stats["checkin_count"] or 0,
                "avg_rating": round(float(checkin_stats["avg_rating"] or 0), 1),
                "signin_rate": f"{signin_count / max(reg_count, 1) * 100:.0f}%"
            }

    def get_club_members(self, club_id: int, role: str = None) -> Dict:
        """获取社团成员列表"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            sql = """
                SELECT cm.id, cm.role, cm.joined_at,
                       u.real_name, u.email
                FROM club_member cm
                JOIN sys_user u ON cm.user_id = u.id
                WHERE cm.club_id = %s AND cm.status = 1 AND cm.deleted = 0
            """
            params = [club_id]
            if role:
                sql += " AND cm.role = %s"
                params.append(role)
            sql += " ORDER BY cm.joined_at LIMIT 20"
            cursor.execute(sql, params)
            members = cursor.fetchall()
            return {
                "club_id": club_id,
                "count": len(members),
                "members": [{k: str(v) if v is not None else "" for k, v in m.items()} for m in members]
            }

    def list_all_clubs(self) -> Dict:
        """获取所有社团列表（概览信息）"""
        with self.conn.cursor(pymysql.cursors.DictCursor) as cursor:
            cursor.execute("""
                SELECT c.id, c.club_name, c.category, c.member_count, c.description,
                       u.real_name as president_name
                FROM club_info c
                LEFT JOIN sys_user u ON c.president_id = u.id
                WHERE c.deleted = 0
                ORDER BY c.id
            """)
            clubs = cursor.fetchall()
            return {
                "count": len(clubs),
                "clubs": [{k: str(v) if v is not None else "" for k, v in c.items()} for c in clubs]
            }

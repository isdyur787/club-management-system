from pydantic import BaseModel, Field
from typing import List, Dict, Optional, Literal

class ChatRequest(BaseModel):
    club_id: int = Field(..., ge=1)
    session_id: Optional[str] = Field(default=None, max_length=64)
    message: str = Field(..., min_length=1, max_length=2000)

class ChatResponse(BaseModel):
    session_id: str
    answer: str
    sources: List[Dict]

class ReportGenerateRequest(BaseModel):
    activity_id: int
    feedback: Optional[str] = None

class ReportReviewRequest(BaseModel):
    action: Literal["approve", "reject", "revise"]
    feedback: Optional[str] = None

class ReportResponse(BaseModel):
    report_id: int
    activity_id: int
    status: str
    title: Optional[str] = None
    content: Optional[str] = None
    download_url: Optional[str] = None

class ExperienceRequest(BaseModel):
    club_id: int
    activity_type: str
    title: str
    location: Optional[str] = None
    max_participants: Optional[int] = None
    budget: Optional[float] = None

class ExperienceResponse(BaseModel):
    has_history: bool
    experience_summary: str
    suggestions: List[str]
    risk_warnings: List[str]
    template: Optional[Dict] = None

class SessionCreateRequest(BaseModel):
    club_id: int = Field(..., ge=1, description="社团ID")

class KnowledgeDocResponse(BaseModel):
    id: str
    club_id: int
    doc_type: str
    title: str
    source_type: str
    chunk_count: int
    created_at: str

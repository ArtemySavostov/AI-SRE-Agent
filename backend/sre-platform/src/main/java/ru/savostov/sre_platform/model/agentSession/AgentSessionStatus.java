package ru.savostov.sre_platform.model.agentSession;

public enum AgentSessionStatus {
    PENDING, RUNNING, WAITING_APPROVAL, COMPLETED, FAILED, CANCELLED;

    public boolean canTransitionTo(AgentSessionStatus next) {
        return switch (this) {
            case PENDING -> next == RUNNING || next == CANCELLED;
            case RUNNING -> next == WAITING_APPROVAL || next == COMPLETED || next == FAILED || next == CANCELLED;
            case WAITING_APPROVAL -> next == RUNNING || next == FAILED || next == CANCELLED;
            default -> false;
        };
    }
}

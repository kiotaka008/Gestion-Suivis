-- ProjectManager SaaS
-- Initial PostgreSQL schema
-- Requires PostgreSQL 15+

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE user_role AS ENUM ('admin', 'manager', 'member');
CREATE TYPE project_status AS ENUM ('active', 'on_hold', 'completed', 'archived');
CREATE TYPE item_priority AS ENUM ('low', 'medium', 'high', 'urgent');
CREATE TYPE project_member_role AS ENUM ('owner', 'member');
CREATE TYPE activity_status AS ENUM ('todo', 'in_progress', 'done', 'blocked');
CREATE TYPE notification_type AS ENUM ('project', 'activity', 'comment', 'deadline', 'ai', 'user');
CREATE TYPE ai_message_role AS ENUM ('user', 'assistant');

CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT organizations_name_not_blank CHECK (BTRIM(name) <> ''),
    CONSTRAINT organizations_slug_not_blank CHECK (BTRIM(slug) <> '')
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    role user_role NOT NULL DEFAULT 'member',
    avatar_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT users_org_id_id_unique UNIQUE (organization_id, id),
    CONSTRAINT users_org_email_unique UNIQUE (organization_id, email),
    CONSTRAINT users_email_not_blank CHECK (BTRIM(email) <> ''),
    CONSTRAINT users_name_not_blank CHECK (BTRIM(name) <> '')
);

CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status project_status NOT NULL DEFAULT 'active',
    priority item_priority NOT NULL DEFAULT 'medium',
    progress INTEGER NOT NULL DEFAULT 0,
    owner_id UUID NOT NULL,
    start_date DATE,
    due_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT projects_org_id_id_unique UNIQUE (organization_id, id),
    CONSTRAINT projects_owner_same_org_fk
        FOREIGN KEY (organization_id, owner_id)
        REFERENCES users (organization_id, id) ON DELETE RESTRICT,
    CONSTRAINT projects_progress_range CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT projects_dates_valid CHECK (due_date IS NULL OR start_date IS NULL OR due_date >= start_date),
    CONSTRAINT projects_name_not_blank CHECK (BTRIM(name) <> '')
);

CREATE TABLE project_members (
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role project_member_role NOT NULL DEFAULT 'member',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT project_members_project_same_org_fk
        FOREIGN KEY (organization_id, project_id)
        REFERENCES projects (organization_id, id) ON DELETE CASCADE,
    CONSTRAINT project_members_user_same_org_fk
        FOREIGN KEY (organization_id, user_id)
        REFERENCES users (organization_id, id) ON DELETE CASCADE
);

CREATE TABLE activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    project_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status activity_status NOT NULL DEFAULT 'todo',
    priority item_priority NOT NULL DEFAULT 'medium',
    progress INTEGER NOT NULL DEFAULT 0,
    assignee_id UUID,
    due_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT activities_org_id_id_unique UNIQUE (organization_id, id),
    CONSTRAINT activities_project_same_org_fk
        FOREIGN KEY (organization_id, project_id)
        REFERENCES projects (organization_id, id) ON DELETE CASCADE,
    CONSTRAINT activities_progress_range CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT activities_title_not_blank CHECK (BTRIM(title) <> '')
);

-- Keep the tenant key intact. The API must clear assignee_id before
-- deleting an assigned user, or reassign the activity.
ALTER TABLE activities
    ADD CONSTRAINT activities_assignee_same_org_fk
    FOREIGN KEY (organization_id, assignee_id)
    REFERENCES users (organization_id, id) ON DELETE RESTRICT;

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    type notification_type NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT notifications_user_same_org_fk
        FOREIGN KEY (organization_id, user_id)
        REFERENCES users (organization_id, id) ON DELETE CASCADE
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organizations(id) ON DELETE SET NULL,
    user_id UUID,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT audit_logs_user_same_org_fk
        FOREIGN KEY (organization_id, user_id)
        REFERENCES users (organization_id, id) ON DELETE SET NULL
);

-- Audit records may outlive an organization/user. Their nullable references
-- intentionally preserve the historical record.

CREATE TABLE ai_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    title VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ai_conversations_org_id_id_unique UNIQUE (organization_id, id),
    CONSTRAINT ai_conversations_user_same_org_fk
        FOREIGN KEY (organization_id, user_id)
        REFERENCES users (organization_id, id) ON DELETE CASCADE
);

CREATE TABLE ai_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL,
    role ai_message_role NOT NULL,
    content TEXT NOT NULL,
    tokens_used INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ai_messages_conversation_same_org_fk
        FOREIGN KEY (organization_id, conversation_id)
        REFERENCES ai_conversations (organization_id, id) ON DELETE CASCADE,
    CONSTRAINT ai_messages_tokens_nonnegative CHECK (tokens_used IS NULL OR tokens_used >= 0),
    CONSTRAINT ai_messages_content_not_blank CHECK (BTRIM(content) <> '')
);

CREATE TABLE ai_usage_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    model VARCHAR(100) NOT NULL,
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    cost_usd NUMERIC(12, 6) NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ai_usage_user_same_org_fk
        FOREIGN KEY (organization_id, user_id)
        REFERENCES users (organization_id, id) ON DELETE CASCADE,
    CONSTRAINT ai_usage_tokens_nonnegative CHECK (prompt_tokens >= 0 AND completion_tokens >= 0),
    CONSTRAINT ai_usage_cost_nonnegative CHECK (cost_usd >= 0)
);

-- Indexes used by common API filters and joins.
CREATE INDEX idx_users_organization_id ON users (organization_id);
CREATE INDEX idx_users_email ON users (email);

CREATE INDEX idx_projects_organization_id ON projects (organization_id);
CREATE INDEX idx_projects_status ON projects (status);
CREATE INDEX idx_projects_owner_id ON projects (owner_id);
CREATE INDEX idx_projects_due_date ON projects (due_date);
CREATE INDEX idx_projects_org_status ON projects (organization_id, status);

CREATE INDEX idx_project_members_organization_id ON project_members (organization_id);
CREATE INDEX idx_project_members_user_id ON project_members (user_id);

CREATE INDEX idx_activities_organization_id ON activities (organization_id);
CREATE INDEX idx_activities_project_id ON activities (project_id);
CREATE INDEX idx_activities_assignee_id ON activities (assignee_id);
CREATE INDEX idx_activities_status ON activities (status);
CREATE INDEX idx_activities_due_date ON activities (due_date);
CREATE INDEX idx_activities_org_status ON activities (organization_id, status);

CREATE INDEX idx_notifications_user_read ON notifications (user_id, is_read);
CREATE INDEX idx_notifications_organization_id ON notifications (organization_id);

CREATE INDEX idx_audit_logs_org_created ON audit_logs (organization_id, created_at DESC);
CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);

CREATE INDEX idx_ai_conversations_org_user ON ai_conversations (organization_id, user_id);
CREATE INDEX idx_ai_messages_conversation_id ON ai_messages (conversation_id);
CREATE INDEX idx_ai_messages_org_conversation ON ai_messages (organization_id, conversation_id);
CREATE INDEX idx_ai_usage_org_created ON ai_usage_events (organization_id, created_at DESC);

-- RLS is an additional defense. The API must set app.current_organization_id
-- per transaction/session after authenticating the user.
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE project_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE activities ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai_usage_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY users_tenant_policy ON users
    USING (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID);

CREATE POLICY projects_tenant_policy ON projects
    USING (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID);

CREATE POLICY project_members_tenant_policy ON project_members
    USING (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID);

CREATE POLICY activities_tenant_policy ON activities
    USING (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID);

CREATE POLICY notifications_tenant_policy ON notifications
    USING (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID);

CREATE POLICY audit_logs_tenant_policy ON audit_logs
    USING (
        organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID
    )
    WITH CHECK (
        organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID
    );

CREATE POLICY ai_conversations_tenant_policy ON ai_conversations
    USING (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID);

CREATE POLICY ai_messages_tenant_policy ON ai_messages
    USING (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID);

CREATE POLICY ai_usage_events_tenant_policy ON ai_usage_events
    USING (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_organization_id', TRUE), '')::UUID);

COMMIT;

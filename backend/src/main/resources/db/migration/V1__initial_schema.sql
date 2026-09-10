CREATE TABLE scenarios (
 id uuid PRIMARY KEY, document jsonb NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE runs (
 id uuid PRIMARY KEY, scenario_id uuid REFERENCES scenarios(id),
 snapshot jsonb NOT NULL, status text NOT NULL,
 result jsonb, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX runs_created_idx ON runs(created_at DESC);
CREATE TABLE events (
 id uuid PRIMARY KEY, name text NOT NULL, body text NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);

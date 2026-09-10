import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import "./style.css";

const emptyStep = () => ({
  path: "/fixed/payment",
  body: '{"paymentId":"{{runId}}"}',
  copies: 1,
  parallel: false,
  delayMs: 0,
  expectedStatus: 200,
  pointer: "/accepted",
  expected: true,
});
const example = (broken = false) => ({
  name: broken
    ? "Duplicate payment · broken handler"
    : "Duplicate payment · fixed handler",
  target: "http://demo:8090",
  steps: [
    {
      ...emptyStep(),
      path: broken ? "/broken/payment" : "/fixed/payment",
      copies: 5,
      parallel: true,
    },
  ],
  probe: {
    path: (broken ? "/broken" : "/fixed") + "/orders/{{runId}}",
    pointer: "/count",
    expected: 1,
    timeoutMs: 3000,
  },
});
function Badge({ status }) {
  return <span className={"badge " + status?.toLowerCase()}>{status}</span>;
}
function App() {
  const [key, setKey] = useState(sessionStorage.getItem("lab-key") || "");
  const [inputKey, setInputKey] = useState(""),
    [page, setPage] = useState("scenarios"),
    [scenarios, setScenarios] = useState([]),
    [runs, setRuns] = useState([]),
    [events, setEvents] = useState([]);
  const [origins, setOrigins] = useState([]),
    [draft, setDraft] = useState(null),
    [editing, setEditing] = useState(null),
    [selected, setSelected] = useState(null);
  const [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [busy, setBusy] = useState(false),
    [eventName, setEventName] = useState(""),
    [eventBody, setEventBody] = useState("{}");
  async function api(path, method = "GET", body) {
    const res = await fetch("/api" + path, {
      method,
      headers: { "X-Lab-Key": key, "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!res.ok) {
      if (res.status === 401) {
        sessionStorage.removeItem("lab-key");
        setKey("");
        throw Error("API key was not accepted.");
      }
      const data = await res.json().catch(() => ({}));
      throw Error(data.message || "Request failed: HTTP " + res.status);
    }
    return res.json();
  }
  async function refresh() {
    const [s, r, e, settings] = await Promise.all([
      api("/scenarios"),
      api("/runs"),
      api("/events"),
      api("/settings"),
    ]);
    setScenarios(s);
    setRuns(r);
    setEvents(e);
    setOrigins(settings.origins);
  }
  async function action(fn) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await fn();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    if (key) refresh().catch((e) => setError(e.message));
  }, [key]);
  useEffect(() => {
    if (!key) return;
    const timer = setInterval(() => {
      api("/runs")
        .then(setRuns)
        .catch(() => {});
      if (selected?.status === "RUNNING")
        api("/runs/" + selected.id)
          .then(setSelected)
          .catch(() => {});
    }, 1500);
    return () => clearInterval(timer);
  }, [key, selected?.id, selected?.status]);
  function openDraft(value, id = null) {
    setDraft(structuredClone(value));
    setEditing(id);
    setPage("scenarios");
    setError("");
  }
  function stepChange(i, field, value) {
    setDraft({
      ...draft,
      steps: draft.steps.map((s, n) =>
        n === i ? { ...s, [field]: value } : s,
      ),
    });
  }
  function move(i, offset) {
    const steps = [...draft.steps];
    [steps[i], steps[i + offset]] = [steps[i + offset], steps[i]];
    setDraft({ ...draft, steps });
  }
  async function save() {
    const response = await api(
      "/scenarios" + (editing ? "/" + editing : ""),
      editing ? "PUT" : "POST",
      draft,
    );
    setEditing(response.id);
    await refresh();
    setNotice("Scenario saved. Future runs use this version.");
  }
  async function start(id) {
    const result = await api("/scenarios/" + id + "/runs", "POST");
    setSelected(await api("/runs/" + result.id));
    setPage("runs");
    setDraft(null);
    await refresh();
  }
  async function downloadReport() {
    const res = await fetch("/api/runs/" + selected.id + "/junit", {
      headers: { "X-Lab-Key": key },
    });
    if (!res.ok) throw Error("Report is not available yet");
    download(
      await res.text(),
      "run-" + selected.id + ".xml",
      "application/xml",
    );
  }
  function download(text, name, type = "application/json") {
    const url = URL.createObjectURL(new Blob([text], { type }));
    const a = document.createElement("a");
    a.href = url;
    a.download = name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  if (!key)
    return (
      <div className="login">
        <div className="login-art">
          <div className="logo-mark">IL</div>
          <p className="eyebrow">INTEGRATION LAB / LOCAL WORKSPACE</p>
          <h1>
            Confidence at
            <br />
            every boundary.
          </h1>
          <p>Turn unexpected deliveries into repeatable tests.</p>
          <div className="flow">
            <span>Event</span>
            <b>→</b>
            <span>Scenario</span>
            <b>→</b>
            <span>Verified</span>
          </div>
        </div>
        <form
          className="login-form"
          onSubmit={(e) => {
            e.preventDefault();
            sessionStorage.setItem("lab-key", inputKey);
            setKey(inputKey);
            setError("");
          }}
        >
          <p className="eyebrow">WELCOME TO YOUR LAB</p>
          <h2>Connect to workspace</h2>
          <p>
            Enter LAB_API_KEY from your local .env file. The key stays in this
            browser tab.
          </p>
          <label>
            Workspace API key
            <input
              type="password"
              autoComplete="off"
              required
              value={inputKey}
              onChange={(e) => setInputKey(e.target.value)}
            />
          </label>
          <button className="primary">Open workspace →</button>
          {error && (
            <p role="alert" className="error">
              {error}
            </p>
          )}
          <small>Self-hosted · Java + React · No AI required</small>
        </form>
      </div>
    );
  return (
    <div className="shell">
      <aside>
        <div className="brand">
          <span className="logo-mark">IL</span>
          <div>
            Integration Lab<small>RELIABILITY WORKSPACE</small>
          </div>
        </div>
        <div className="workspace">
          <span className="dot" />
          Local installation<small>One workspace. Every scenario.</small>
        </div>
        <p className="nav-caption">WORKSPACE</p>
        <nav>
          {[
            ["scenarios", "◈", "Scenarios"],
            ["runs", "↗", "Test runs"],
            ["events", "≋", "Event library"],
          ].map(([p, icon, label]) => (
            <button
              className={page === p ? "active" : ""}
              key={p}
              onClick={() => {
                setPage(p);
                setDraft(null);
                setSelected(null);
                setError("");
              }}
            >
              <span>{icon}</span>
              {label}
              <small>
                {p === "scenarios"
                  ? scenarios.length
                  : p === "runs"
                    ? runs.length
                    : events.length}
              </small>
            </button>
          ))}
        </nav>
        <div className="aside-bottom">
          <p>Built for the unexpected.</p>
          <small>Duplicates. Delays. Real outcomes.</small>
          <button
            onClick={() => {
              sessionStorage.removeItem("lab-key");
              setKey("");
              setSelected(null);
            }}
          >
            Disconnect workspace
          </button>
        </div>
      </aside>
      <main>
        <header>
          <span>
            Workspace <b>/</b>{" "}
            {page === "scenarios"
              ? "Scenarios"
              : page === "runs"
                ? "Test runs"
                : "Event library"}
          </span>
          <span className="local">
            <span className="dot" /> SELF-HOSTED MVP
          </span>
        </header>
        <div className="content">
          {error && (
            <div className="error" role="alert">
              {error}
            </div>
          )}
          {notice && (
            <div className="notice" role="status">
              {notice}
            </div>
          )}
          {page === "scenarios" && !draft && (
            <>
              <div className="page-title">
                <div>
                  <p className="eyebrow">MAKE FAILURE REPEATABLE</p>
                  <h1>Your integration test bench</h1>
                  <p>
                    Build a scenario. Challenge your handler. Verify what
                    actually happened.
                  </p>
                </div>
                <button
                  className="primary"
                  onClick={() =>
                    openDraft({
                      ...example(),
                      name: "Untitled scenario",
                      target: origins[0] || "http://demo:8090",
                    })
                  }
                >
                  + New scenario
                </button>
              </div>
              <div className="stats">
                <div>
                  <span>Saved scenarios</span>
                  <strong>
                    {scenarios.length.toString().padStart(2, "0")}
                  </strong>
                  <small>Your repeatable checks</small>
                </div>
                <div>
                  <span>Passed runs</span>
                  <strong>
                    {runs
                      .filter((r) => r.status === "PASSED")
                      .length.toString()
                      .padStart(2, "0")}
                  </strong>
                  <small>Within the latest 100 runs</small>
                </div>
                <div>
                  <span>Needs attention</span>
                  <strong className="amber">
                    {runs
                      .filter((r) =>
                        ["FAILED", "ERROR", "INTERRUPTED"].includes(r.status),
                      )
                      .length.toString()
                      .padStart(2, "0")}
                  </strong>
                  <small>Inspect the evidence</small>
                </div>
              </div>
              <section className="starter">
                <div>
                  <p className="eyebrow">START WITH A REAL FAILURE MODE</p>
                  <h2>
                    One payment. Five deliveries.
                    <br />
                    How many orders?
                  </h2>
                  <p>
                    Explore the demo shop with an idempotent handler or its
                    deliberately broken counterpart.
                  </p>
                  <div className="buttons">
                    <button onClick={() => openDraft(example())}>
                      Try fixed handler →
                    </button>
                    <button
                      className="ghost"
                      onClick={() => openDraft(example(true))}
                    >
                      Try broken handler
                    </button>
                  </div>
                </div>
                <div className="mini-flow">
                  <span>
                    payment.received <b>× 5</b>
                  </span>
                  <i>↓</i>
                  <span>
                    Business assertion <b>orders = 1</b>
                  </span>
                </div>
              </section>
              <section>
                <div className="section-title">
                  <h2>Saved scenarios</h2>
                  <label className="file-button">
                    Import JSON
                    <input
                      type="file"
                      accept=".json"
                      onChange={(e) =>
                        action(async () => {
                          const f = e.target.files[0];
                          if (f) {
                            if (f.size > 262144)
                              throw Error("File is too large");
                            const value = JSON.parse(await f.text());
                            if (!value.name || !Array.isArray(value.steps))
                              throw Error("Invalid scenario file");
                            openDraft(value);
                          }
                        })
                      }
                    />
                  </label>
                </div>
                {!scenarios.length ? (
                  <div className="empty">
                    <h3>Your first scenario starts here</h3>
                    <p>
                      Choose a demo above, or create a scenario for your own
                      handler.
                    </p>
                  </div>
                ) : (
                  <div className="table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>SCENARIO</th>
                          <th>TARGET</th>
                          <th>DELIVERIES</th>
                          <th>ACTIONS</th>
                        </tr>
                      </thead>
                      <tbody>
                        {scenarios.map((s) => (
                          <tr key={s.id}>
                            <td>
                              <button
                                className="text-button"
                                onClick={() => openDraft(s.scenario, s.id)}
                              >
                                {s.scenario.name}
                              </button>
                              <small>
                                {s.scenario.steps.length} steps ·{" "}
                                {s.scenario.probe
                                  ? "Business probe"
                                  : "HTTP checks"}
                              </small>
                            </td>
                            <td>
                              <code>{s.scenario.target}</code>
                            </td>
                            <td>
                              {s.scenario.steps.reduce(
                                (n, x) => n + x.copies,
                                0,
                              )}
                            </td>
                            <td>
                              <button
                                disabled={busy}
                                onClick={() => action(() => start(s.id))}
                              >
                                Run →
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </>
          )}
          {page === "scenarios" && draft && (
            <>
              <div className="page-title">
                <div>
                  <p className="eyebrow">SCENARIO EDITOR</p>
                  <h1>{editing ? "Edit scenario" : "Build your next check"}</h1>
                  <p>
                    Steps run in order. Parallel copies run within a single
                    step.
                  </p>
                </div>
                <button onClick={() => setDraft(null)}>
                  Back to scenarios
                </button>
              </div>
              <div className="editor">
                <section className="panel">
                  <div className="section-title">
                    <h2>01 / Destination</h2>
                    <button
                      onClick={() =>
                        download(
                          JSON.stringify(draft, null, 2),
                          "scenario.json",
                        )
                      }
                    >
                      Export JSON
                    </button>
                  </div>
                  <div className="grid2">
                    <label>
                      Scenario name
                      <input
                        value={draft.name || ""}
                        onChange={(e) =>
                          setDraft({ ...draft, name: e.target.value })
                        }
                        maxLength={120}
                      />
                    </label>
                    <label>
                      Target origin
                      <select
                        value={draft.target}
                        onChange={(e) =>
                          setDraft({ ...draft, target: e.target.value })
                        }
                      >
                        {[...new Set([...origins, draft.target])].map((o) => (
                          <option key={o}>{o}</option>
                        ))}
                      </select>
                    </label>
                  </div>
                  <small>
                    Targets are restricted by the server operator. Use{" "}
                    {"{{runId}}"} for unique test data.
                  </small>
                </section>
                <section className="panel">
                  <div className="section-title">
                    <h2>02 / Delivery sequence</h2>
                    <button
                      disabled={draft.steps.length >= 20}
                      onClick={() =>
                        setDraft({
                          ...draft,
                          steps: [...draft.steps, emptyStep()],
                        })
                      }
                    >
                      + Add step
                    </button>
                  </div>
                  {draft.steps.map((s, i) => (
                    <div className="step" key={i}>
                      <div className="section-title">
                        <h3>
                          Step {i + 1} <span className="method">POST</span>
                        </h3>
                        <div className="buttons">
                          <button
                            aria-label={"Move step " + (i + 1) + " up"}
                            disabled={i === 0}
                            onClick={() => move(i, -1)}
                          >
                            ↑
                          </button>
                          <button
                            aria-label={"Move step " + (i + 1) + " down"}
                            disabled={i === draft.steps.length - 1}
                            onClick={() => move(i, 1)}
                          >
                            ↓
                          </button>
                          <button
                            disabled={draft.steps.length === 1}
                            onClick={() =>
                              setDraft({
                                ...draft,
                                steps: draft.steps.filter((_, n) => n !== i),
                              })
                            }
                          >
                            Remove
                          </button>
                        </div>
                      </div>
                      <label>
                        Request path
                        <input
                          value={s.path}
                          onChange={(e) =>
                            stepChange(i, "path", e.target.value)
                          }
                        />
                      </label>
                      <label>
                        Request body
                        <textarea
                          className="code"
                          rows={4} aria-label="Request body"
                          value={s.body}
                          onChange={(e) =>
                            stepChange(i, "body", e.target.value)
                          }
                        />
                      </label>
                      {events.length > 0 && (
                        <label>
                          Use saved event
                          <select
                            defaultValue=""
                            onChange={(e) => {
                              const event = events.find(
                                (x) => x.id === e.target.value,
                              );
                              if (event) stepChange(i, "body", event.body);
                            }}
                          >
                            <option value="">Select an event…</option>
                            {events.map((x) => (
                              <option key={x.id} value={x.id}>
                                {x.name}
                              </option>
                            ))}
                          </select>
                        </label>
                      )}
                      <div className="grid3">
                        <label>
                          Copies
                          <input
                            type="number"
                            min="1"
                            max="10"
                            value={s.copies}
                            onChange={(e) =>
                              stepChange(i, "copies", Number(e.target.value))
                            }
                          />
                        </label>
                        <label>
                          Delay before step (ms)
                          <input
                            type="number"
                            min="0"
                            max="5000"
                            value={s.delayMs}
                            onChange={(e) =>
                              stepChange(i, "delayMs", Number(e.target.value))
                            }
                          />
                        </label>
                        <label>
                          Expected HTTP status
                          <input
                            type="number"
                            min="100"
                            max="599"
                            value={s.expectedStatus}
                            onChange={(e) =>
                              stepChange(
                                i,
                                "expectedStatus",
                                Number(e.target.value),
                              )
                            }
                          />
                        </label>
                      </div>
                      <label className="check">
                        <input
                          type="checkbox"
                          checked={s.parallel}
                          onChange={(e) =>
                            stepChange(i, "parallel", e.target.checked)
                          }
                        />
                        Send copies concurrently
                      </label>
                      <div className="grid2">
                        <label>
                          JSON Pointer (optional)
                          <input
                            placeholder="/accepted"
                            value={s.pointer || ""}
                            onChange={(e) =>
                              stepChange(i, "pointer", e.target.value)
                            }
                          />
                        </label>
                        <JsonValue
                          label="Expected JSON value (blank disables)"
                          value={s.expected}
                          onChange={(value) => stepChange(i, "expected", value)}
                        />
                      </div>
                    </div>
                  ))}
                </section>
                <section className="panel">
                  <h2>03 / Verify business outcome</h2>
                  <label className="check">
                    <input
                      type="checkbox"
                      checked={!!draft.probe}
                      onChange={(e) =>
                        setDraft({
                          ...draft,
                          probe: e.target.checked ? example().probe : null,
                        })
                      }
                    />
                    Poll a business API after delivery
                  </label>
                  {draft.probe && (
                    <>
                      <label>
                        GET path
                        <input
                          value={draft.probe.path}
                          onChange={(e) =>
                            setDraft({
                              ...draft,
                              probe: { ...draft.probe, path: e.target.value },
                            })
                          }
                        />
                      </label>
                      <div className="grid3">
                        <label>
                          JSON Pointer
                          <input
                            value={draft.probe.pointer}
                            onChange={(e) =>
                              setDraft({
                                ...draft,
                                probe: {
                                  ...draft.probe,
                                  pointer: e.target.value,
                                },
                              })
                            }
                          />
                        </label>
                        <JsonValue
                          label="Expected JSON value"
                          value={draft.probe.expected}
                          onChange={(v) =>
                            setDraft({
                              ...draft,
                              probe: { ...draft.probe, expected: v },
                            })
                          }
                        />
                        <label>
                          Timeout (ms)
                          <input
                            type="number"
                            min="100"
                            max="10000"
                            value={draft.probe.timeoutMs}
                            onChange={(e) =>
                              setDraft({
                                ...draft,
                                probe: {
                                  ...draft.probe,
                                  timeoutMs: Number(e.target.value),
                                },
                              })
                            }
                          />
                        </label>
                      </div>
                    </>
                  )}
                </section>
                <div className="editor-footer">
                  <span>Saved scenarios are snapshotted on every run.</span>
                  <div className="buttons">
                    <button disabled={busy} onClick={() => action(save)}>
                      Save scenario
                    </button>
                    <button
                      className="primary"
                      disabled={busy}
                      onClick={() =>
                        action(async () => {
                          const data = await api(
                            "/scenarios" + (editing ? "/" + editing : ""),
                            editing ? "PUT" : "POST",
                            draft,
                          );
                          await start(data.id);
                        })
                      }
                    >
                      {busy ? "Working…" : "Save & run →"}
                    </button>
                  </div>
                </div>
              </div>
            </>
          )}
          {page === "runs" && (
            <>
              <div className="page-title">
                <div>
                  <p className="eyebrow">EVIDENCE, NOT ASSUMPTIONS</p>
                  <h1>{selected ? "Run report" : "Test runs"}</h1>
                  <p>
                    Every response and business assertion, captured in one
                    place.
                  </p>
                </div>
                {selected && (
                  <button onClick={() => setSelected(null)}>All runs</button>
                )}
              </div>
              {selected ? (
                <>
                  <div className="panel">
                    <div className="section-title">
                      <div>
                        <h2>{selected.scenario.name}</h2>
                        <code>{selected.id}</code>
                      </div>
                      <Badge status={selected.status} />
                    </div>
                    {selected.result ? (
                      <>
                        <p
                          className={
                            selected.result.probeMessage ===
                            "Business assertion passed"
                              ? "notice"
                              : "probe-message"
                          }
                        >
                          {selected.result.probeMessage}
                        </p>
                        <div className="section-title">
                          <small>
                            {selected.result.elapsedMs} ms ·{" "}
                            {selected.result.deliveries.length} deliveries
                          </small>
                          <button onClick={() => action(downloadReport)}>
                            Download JUnit XML
                          </button>
                        </div>
                        {selected.result.deliveries.map((d, i) => (
                          <details key={i} className="delivery">
                            <summary>
                              <span
                                className={
                                  "outcome " + (d.passed ? "ok" : "bad")
                                }
                              >
                                {d.passed ? "✓" : "×"}
                              </span>
                              <strong>
                                Step {d.step} · Copy {d.copy}
                              </strong>
                              <span>HTTP {d.status || "—"}</span>
                              <span>{d.elapsedMs} ms</span>
                            </summary>
                            <p>{d.message}</p>
                            <pre>{d.body || "No response body"}</pre>
                          </details>
                        ))}
                      </>
                    ) : (
                      <div className="empty">
                        <h3>
                          {selected.status === "RUNNING"
                            ? "Scenario is running…"
                            : "Run was interrupted"}
                        </h3>
                        <p>
                          {selected.status === "RUNNING"
                            ? "This report updates automatically."
                            : "Delivery outcomes may be unknown. Start a fresh run with isolated test data."}
                        </p>
                      </div>
                    )}
                  </div>
                  <details className="panel">
                    <summary>Scenario snapshot</summary>
                    <pre>{JSON.stringify(selected.scenario, null, 2)}</pre>
                  </details>
                </>
              ) : runs.length ? (
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>SCENARIO</th>
                        <th>RESULT</th>
                        <th>STARTED</th>
                        <th />
                      </tr>
                    </thead>
                    <tbody>
                      {runs.map((r) => (
                        <tr key={r.id}>
                          <td>
                            {r.name}
                            <small>{r.id.slice(0, 8)}</small>
                          </td>
                          <td>
                            <Badge status={r.status} />
                          </td>
                          <td>{new Date(r.createdAt).toLocaleString()}</td>
                          <td>
                            <button
                              onClick={() =>
                                action(async () =>
                                  setSelected(await api("/runs/" + r.id)),
                                )
                              }
                            >
                              View report →
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="empty">
                  <h3>No runs yet</h3>
                  <p>Start with the fixed and broken demo scenarios.</p>
                </div>
              )}
            </>
          )}
          {page === "events" && (
            <>
              <div className="page-title">
                <div>
                  <p className="eyebrow">REUSABLE INPUTS</p>
                  <h1>Event library</h1>
                  <p>
                    Save fixtures or receive requests at
                    /api/inbox/YOUR_INBOX_TOKEN.
                  </p>
                </div>
                <button onClick={() => action(refresh)}>Refresh</button>
              </div>
              <section className="panel">
                <div className="grid2">
                  <label>
                    Event name
                    <input
                      value={eventName}
                      onChange={(e) => setEventName(e.target.value)}
                    />
                  </label>
                  <label>
                    Load body from file
                    <input
                      type="file"
                      accept=".json,.txt"
                      onChange={(e) =>
                        action(async () => {
                          const f = e.target.files[0];
                          if (f) {
                            if (f.size > 32000)
                              throw Error("Event body exceeds 32000 bytes");
                            setEventBody(await f.text());
                            setEventName(f.name);
                          }
                        })
                      }
                    />
                  </label>
                </div>
                <label>
                  Body
                  <textarea
                    className="code"
                    rows={6} aria-label="Body"
                    value={eventBody}
                    onChange={(e) => setEventBody(e.target.value)}
                  />
                </label>
                <button
                  className="primary"
                  disabled={busy || !eventName.trim()}
                  onClick={() =>
                    action(async () => {
                      await api("/events", "POST", {
                        name: eventName,
                        body: eventBody,
                      });
                      setEventName("");
                      await refresh();
                      setNotice(
                        "Event saved. Select it inside a scenario step.",
                      );
                    })
                  }
                >
                  Save event
                </button>
              </section>
              {events.map((e) => (
                <details className="panel" key={e.id}>
                  <summary>
                    {e.name}{" "}
                    <small>{new Date(e.createdAt).toLocaleString()}</small>
                  </summary>
                  <pre>{e.body}</pre>
                </details>
              ))}
            </>
          )}
          <footer>
            Integration Lab <span>Local-first integration testing · v0.1</span>
          </footer>
        </div>
      </main>
    </div>
  );
}
function JsonValue({ label, value, onChange }) {
  const [text, setText] = useState(value == null ? "" : JSON.stringify(value)),
    [invalid, setInvalid] = useState(false);
  useEffect(() => {
    setText(value == null ? "" : JSON.stringify(value));
    setInvalid(false);
  }, [value]);
  return (
    <label>
      {label}
      <input
        className={invalid ? "invalid" : ""}
        value={text}
        onChange={(e) => {
          setText(e.target.value);
          try {
            onChange(e.target.value.trim() ? JSON.parse(e.target.value) : null);
            setInvalid(false);
          } catch {
            setInvalid(true);
          }
        }}
        onBlur={() => {
          if (invalid) {
            setText(value == null ? "" : JSON.stringify(value));
            setInvalid(false);
          }
        }}
      />
      {invalid && (
        <small>
          Enter valid JSON, e.g. true, 1 or "paid". Invalid edits revert on
          blur.
        </small>
      )}
    </label>
  );
}
createRoot(document.getElementById("root")).render(<App />);

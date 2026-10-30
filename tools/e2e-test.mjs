const BASE = process.env.ZHURNAL_API ?? "http://localhost:8080/api";

let pass = 0, fail = 0;
function ok(name, cond, extra = "") {
  if (cond) { pass++; console.log(`  OK   ${name}`); }
  else { fail++; console.log(`  FAIL ${name} ${extra}`); }
}

async function req(method, path, { token, body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      "content-type": "application/json",
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  return { status: res.status, data };
}

const stamp = Date.now();
const testGroupName = `E2E Группа ${stamp}`;
const testGroupCode = `E2E${stamp}`;
const teacherLogin = `e2e_t_${stamp}`;
const studentLogin = `e2e_s_${stamp}`;
const headmanLogin = `e2e_h_${stamp}`;
const password = "test12345";

function addDays(date, days) {
  const copy = new Date(date.getTime());
  copy.setUTCDate(copy.getUTCDate() + days);
  return copy;
}

function isoDate(date) {
  return date.toISOString().slice(0, 10);
}

function mondayOf(date) {
  const copy = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  const offset = (copy.getUTCDay() + 6) % 7;
  copy.setUTCDate(copy.getUTCDate() - offset);
  return copy;
}

function weekTypeOf(date) {
  const week = Math.floor((date.getUTCDate() - 1) / 7) + 1;
  return week % 2 === 1 ? "lower" : "upper";
}

let admin, adminId;
let groupId, otherGroupId = null;
let subjectId, secondSubjectId, splitScheduleId, lessonId, teacherId, studentId, headmanId;
let teacherToken, studentToken, headmanToken;
let scheduleId, homeworkId, secondHomeworkId;

function cleanupIds() {
  return [teacherId, studentId, headmanId].filter((id) => Number.isInteger(id));
}

console.log("== health ==");
{
  const r = await req("GET", "/health");
  ok("health 200", r.status === 200);
  ok("health ok flag", r.data?.ok === true);
  ok("health counts groups", r.data?.counts?.groups >= 1, JSON.stringify(r.data?.counts));
}

console.log("== app update ==");
{
  const r = await req("GET", "/app/update");
  ok("update 200", r.status === 200, String(r.status));
  ok("update version code", Number.isInteger(r.data?.versionCode) && r.data.versionCode > 0);
  ok("update version name", typeof r.data?.versionName === "string" && r.data.versionName.length > 0);
  ok("update apk url", typeof r.data?.apkUrl === "string" && r.data.apkUrl.length > 0);
  ok("update sha256", typeof r.data?.sha256 === "string" && r.data.sha256.length === 64);
  ok("update mandatory", r.data?.mandatory === true);

  const downloadUrl = `${BASE.replace(/\/api$/, "")}${r.data?.apkUrl}`;
  const dl = await fetch(downloadUrl, { method: "HEAD" });
  ok(
    "update apk downloadable",
    dl.status === 200 &&
      String(dl.headers.get("content-type") ?? "").includes("android.package-archive"),
    `${dl.status} ${dl.headers.get("content-type")}`,
  );
}

console.log("== auth ==");
{
  const r = await req("POST", "/auth/login", { body: { login: "admin", password: "admin12345" } });
  ok("admin login", r.status === 200 && !!r.data?.token, `${r.status} ${JSON.stringify(r.data)}`);
  ok("admin role", r.data?.user?.role === "admin");
  admin = r.data?.token;
  adminId = r.data?.user?.id;

  const bad = await req("POST", "/auth/login", { body: { login: "admin", password: "wrong" } });
  ok("bad password rejected 401", bad.status === 401, String(bad.status));
}

console.log("== setup: group, users, subject ==");
{
  const g = await req("POST", "/groups", {
    token: admin,
    body: { name: testGroupName, code: testGroupCode, avatar: "🎓" },
  });
  ok("create group 201", g.status === 201, `${g.status} ${JSON.stringify(g.data)}`);
  groupId = g.data?.group?.id;
  ok("group avatar saved", g.data?.group?.avatar === "🎓", JSON.stringify(g.data?.group));

  const groups = await req("GET", "/groups", { token: admin });
  otherGroupId = groups.data?.groups?.find((x) => x.id !== groupId)?.id ?? null;

  const teacher = await req("POST", "/users", {
    token: admin,
    body: {
      fullName: "E2E Преподаватель",
      login: teacherLogin,
      password,
      role: "teacher",
      avatar: "🧑‍🏫",
    },
  });
  ok("create teacher 201", teacher.status === 201, `${teacher.status} ${JSON.stringify(teacher.data)}`);
  teacherId = teacher.data?.user?.id;
  ok("teacher avatar saved", teacher.data?.user?.avatar === "🧑‍🏫");

  const student = await req("POST", "/users", {
    token: admin,
    body: {
      fullName: "E2E Обучающийся",
      login: studentLogin,
      password,
      role: "student",
      groupId,
      avatar: "🐱",
    },
  });
  ok("create student 201", student.status === 201, `${student.status} ${JSON.stringify(student.data)}`);
  studentId = student.data?.user?.id;
  ok("student avatar saved", student.data?.user?.avatar === "🐱");

  const headman = await req("POST", "/users", {
    token: admin,
    body: {
      fullName: "E2E Староста",
      login: headmanLogin,
      password,
      role: "headman",
      groupId,
      avatar: "⭐",
    },
  });
  ok("create headman 201", headman.status === 201, `${headman.status} ${JSON.stringify(headman.data)}`);
  headmanId = headman.data?.user?.id;
  ok("headman role saved", headman.data?.user?.role === "headman");
  ok("headman avatar saved", headman.data?.user?.avatar === "⭐");

  const noGroupHeadman = await req("POST", "/users", {
    token: admin,
    body: { fullName: "E2E Без группы", login: `e2e_n_${stamp}`, password, role: "headman" },
  });
  ok("headman without group rejected 400", noGroupHeadman.status === 400, String(noGroupHeadman.status));

  const code = await req("POST", "/auth/check-code", { body: { code: testGroupCode } });
  ok("check-code valid", code.status === 200 && code.data?.group?.id === groupId, JSON.stringify(code.data));
  ok("check-code returns group avatar", code.data?.group?.avatar === "🎓", JSON.stringify(code.data?.group));
  const badCode = await req("POST", "/auth/check-code", { body: { code: "NOPE" } });
  ok("check-code invalid 404", badCode.status === 404);

  const t = await req("POST", "/auth/login", { body: { login: teacherLogin, password } });
  ok("teacher login", t.status === 200 && t.data?.user?.role === "teacher");
  teacherToken = t.data?.token;

  const s = await req("POST", "/auth/login", { body: { login: studentLogin, password } });
  ok("student login", s.status === 200 && s.data?.user?.role === "student");
  studentToken = s.data?.token;

  const h = await req("POST", "/auth/login", { body: { login: headmanLogin, password } });
  ok("headman login", h.status === 200 && h.data?.user?.role === "headman");
  headmanToken = h.data?.token;

  const sub = await req("POST", "/subjects", {
    token: admin,
    body: { name: "E2E Предмет", groupId, teacherId },
  });
  ok("create subject 201", sub.status === 201, `${sub.status} ${JSON.stringify(sub.data)}`);
  subjectId = sub.data?.id;
}

console.log("== groups / subjects ==");
{
  const g = await req("GET", "/groups", { token: admin });
  ok("admin sees groups", g.status === 200 && g.data.groups.some((x) => x.id === groupId));

  const gs = await req("GET", "/groups", { token: studentToken });
  ok("student sees own group only", gs.status === 200 && gs.data.groups.length === 1 && gs.data.groups[0].id === groupId);

  const gh = await req("GET", "/groups", { token: headmanToken });
  ok("headman sees own group only", gh.status === 200 && gh.data.groups.length === 1 && gh.data.groups[0].id === groupId);

  const gt = await req("GET", "/groups", { token: teacherToken });
  ok("teacher sees own groups", gt.status === 200 && gt.data.groups.some((x) => x.id === groupId));

  const sub = await req("GET", "/subjects", { token: teacherToken });
  ok("teacher sees subjects", sub.status === 200 && sub.data.subjects.some((x) => x.id === subjectId));
  ok("teacher subjects are own only", sub.data?.subjects?.every((s) => s.teacherId === teacherId));

  const subStudent = await req("GET", "/subjects", { token: studentToken });
  ok("student sees group subjects", subStudent.status === 200 && subStudent.data.subjects.some((x) => x.id === subjectId));
}

console.log("== journal ==");
{
  const j = await req("GET", `/groups/${groupId}/journal`, { token: teacherToken });
  ok("journal 200", j.status === 200, JSON.stringify(j.data)?.slice(0, 200));
  ok("journal has students", Array.isArray(j.data?.students) && j.data.students.length >= 2);
  ok("journal includes headman", j.data?.students?.some((s) => s.id === headmanId), JSON.stringify(j.data?.students));
  ok("journal has subjects", j.data?.subjects?.some((s) => s.id === subjectId));

  const rosterStudent = j.data?.students?.find((s) => s.id === studentId);
  ok("student has avatar in journal", rosterStudent?.avatar === "🐱", JSON.stringify(rosterStudent));
  ok("student has avg number or null", rosterStudent?.avg === null || typeof rosterStudent?.avg === "number");
  ok("student has absentPairs", typeof rosterStudent?.absentPairs === "number");
  ok("student has absentHours", typeof rosterStudent?.absentHours === "number");
  ok("journal stats", typeof j.data?.stats?.average !== "undefined");

  const created = await req("POST", "/lessons", {
    token: teacherToken,
    body: { subjectId, date: "2098-12-30", pairNumber: 8, topic: "E2E тест" },
  });
  ok("teacher creates lesson 201", created.status === 201, `${created.status} ${JSON.stringify(created.data)}`);
  lessonId = created.data?.lesson?.id;
  ok("lesson id returned", !!lessonId);

  if (lessonId) {
    const put = await req("PUT", "/records", {
      token: teacherToken,
      body: { lessonId, studentId, grade: 9, absent: false },
    });
    ok("teacher upserts grade 200", put.status === 200, `${put.status} ${JSON.stringify(put.data)}`);

    const j2 = await req("GET", `/groups/${groupId}/journal`, { token: teacherToken });
    const rec = j2.data?.records?.find((r) => r.lessonId === lessonId && r.studentId === studentId);
    ok("grade persisted", rec?.grade === 9, JSON.stringify(rec));

    const abs = await req("PUT", "/records", {
      token: teacherToken,
      body: { lessonId, studentId, grade: null, absent: true, reason: "Болезнь" },
    });
    ok("upsert absence 200", abs.status === 200);

    const j3 = await req("GET", `/groups/${groupId}/journal`, { token: teacherToken });
    const rec2 = j3.data?.records?.find((r) => r.lessonId === lessonId && r.studentId === studentId);
    ok("absence persisted with reason", rec2?.absent === true && rec2?.reason === "Болезнь", JSON.stringify(rec2));

    const headmanWrite = await req("PUT", "/records", {
      token: headmanToken,
      body: { lessonId, studentId: headmanId, grade: 10, absent: false },
    });
    ok("headman edits own group 200", headmanWrite.status === 200, `${headmanWrite.status} ${JSON.stringify(headmanWrite.data)}`);

    const jh = await req("GET", `/groups/${groupId}/journal`, { token: headmanToken });
    ok("headman journal canEdit", jh.data?.canEdit === true, String(jh.data?.canEdit));
    const hrec = jh.data?.records?.find((r) => r.lessonId === lessonId && r.studentId === headmanId);
    ok("headman grade persisted", hrec?.grade === 10, JSON.stringify(hrec));

    const denied = await req("PUT", "/records", {
      token: studentToken,
      body: { lessonId, studentId, grade: 10 },
    });
    ok("student cannot write 403", denied.status === 403, String(denied.status));

    const badGrade = await req("PUT", "/records", {
      token: teacherToken,
      body: { lessonId, studentId, grade: 11 },
    });
    ok("grade 11 rejected 400", badGrade.status === 400, String(badGrade.status));

    const patched = await req("PATCH", `/lessons/${lessonId}`, {
      token: teacherToken,
      body: { topic: "E2E тест 2" },
    });
    ok("lesson patch", patched.status === 200 && patched.data?.lesson?.topic === "E2E тест 2");
  }

  const studentJournal = await req("GET", `/groups/${groupId}/journal`, { token: studentToken });
  ok("student reads journal", studentJournal.status === 200);
  ok("student cannot edit", studentJournal.data?.canEdit === false);

  if (otherGroupId) {
    const deniedOther = await req("GET", `/groups/${otherGroupId}/journal`, { token: headmanToken });
    ok("headman cannot read other group 403", deniedOther.status === 403, String(deniedOther.status));
  }
}

console.log("== summary ==");
{
  const s = await req("GET", `/groups/${groupId}/summary`, { token: teacherToken });
  ok("summary 200", s.status === 200);
  ok("summary students", Array.isArray(s.data?.students) && s.data.students.length >= 2);
  ok("summary includes headman", s.data?.students?.some((x) => x.id === headmanId));
  ok("summary subject averages", Array.isArray(s.data?.students?.[0]?.subjectAverages));
  const st = s.data?.students?.find((x) => x.id === studentId);
  ok("summary hours = pairs*2", st?.absentHours === st?.absentPairs * 2, JSON.stringify(st));
  ok("summary student has avatar", typeof st?.avatar === "string", JSON.stringify(st));
}

console.log("== avatars ==");
{
  const mine = await req("PATCH", "/auth/me/avatar", {
    token: studentToken,
    body: { avatar: "🦊" },
  });
  ok("student changes own avatar", mine.status === 200 && mine.data?.user?.avatar === "🦊", `${mine.status} ${JSON.stringify(mine.data)}`);

  const me = await req("GET", "/auth/me", { token: studentToken });
  ok("avatar persisted in session", me.data?.user?.avatar === "🦊", JSON.stringify(me.data?.user));

  const cleared = await req("PATCH", "/auth/me/avatar", { token: studentToken, body: { avatar: null } });
  ok("avatar can be cleared", cleared.status === 200 && cleared.data?.user?.avatar === null, JSON.stringify(cleared.data));

  const groupAvatar = await req("PATCH", `/groups/${groupId}`, {
    token: admin,
    body: { avatar: "📚" },
  });
  ok("group avatar changed", groupAvatar.status === 200 && groupAvatar.data?.group?.avatar === "📚", JSON.stringify(groupAvatar.data));
}

console.log("== users (admin) ==");
{
  const u = await req("GET", "/users", { token: admin });
  ok("admin lists users", u.status === 200 && u.data.users.length >= 4);
  ok("admin sees headman filter", await (async () => {
    const h = await req("GET", "/users?role=headman", { token: admin });
    return h.status === 200 && h.data.users.some((x) => x.id === headmanId);
  })());

  const denied = await req("GET", "/users", { token: teacherToken });
  ok("teacher cannot list users 403", denied.status === 403, String(denied.status));
  const denied2 = await req("GET", "/users", { token: studentToken });
  ok("student cannot list users 403", denied2.status === 403, String(denied2.status));

  const patched = await req("PATCH", `/users/${studentId}`, {
    token: admin,
    body: { avatar: "🐼" },
  });
  ok("admin changes user avatar", patched.status === 200 && patched.data?.user?.avatar === "🐼");

  const disabled = await req("PATCH", `/users/${studentId}`, { token: admin, body: { isActive: false } });
  ok("admin disables user", disabled.status === 200 && disabled.data?.user?.isActive === false);
  const enabled = await req("PATCH", `/users/${studentId}`, { token: admin, body: { isActive: true } });
  ok("admin enables user", enabled.status === 200 && enabled.data?.user?.isActive === true);
}

console.log("== audit ==");
{
  const r = await req("GET", `/audit?groupId=${groupId}&limit=200`, { token: admin });
  ok("admin reads audit", r.status === 200 && Array.isArray(r.data?.entries), `${r.status} ${JSON.stringify(r.data)?.slice(0, 120)}`);
  ok("audit has record actions", r.data?.entries?.some((e) => e.action === "record.set"));
  ok("audit has group creation", r.data?.entries?.some((e) => e.action === "group.create"));
  ok("audit has group avatar change", r.data?.entries?.some((e) => e.action === "group.update"));
  ok("audit has avatar change", r.data?.entries?.some((e) => e.action === "user.avatar"));
  ok("audit has total", typeof r.data?.total === "number" && r.data.total >= 5, String(r.data?.total));
  ok("audit entry has actor", r.data?.entries?.every((e) => !!e.userName && !!e.userRole));

  const filtered = await req("GET", "/audit?entityType=record&limit=200", { token: admin });
  ok("audit filter by records", filtered.status === 200 && filtered.data.entries.every((e) => e.entityType === "record"));

  const denied = await req("GET", "/audit", { token: teacherToken });
  ok("teacher cannot read audit 403", denied.status === 403, String(denied.status));
  const denied2 = await req("GET", "/audit", { token: headmanToken });
  ok("headman cannot read audit 403", denied2.status === 403, String(denied2.status));
}

console.log("== substitutions ==");
{
  const r = await req("GET", "/substitutions", { token: studentToken });
  ok("substitutions 200", r.status === 200, `${r.status} ${JSON.stringify(r.data)?.slice(0, 200)}`);
  ok("substitutions has title", typeof r.data?.title === "string" && r.data.title.length > 0);
  ok("substitutions parsed docx text", typeof r.data?.text === "string" && r.data.text.length > 100);
  ok("substitutions lines", Array.isArray(r.data?.lines) && r.data.lines.length > 5);
  ok("substitutions file url", typeof r.data?.fileUrl === "string" && r.data.fileUrl.endsWith(".docx"));
  ok("substitutions blocks parsed", Array.isArray(r.data?.blocks) && r.data.blocks.length > 0);
  const table = r.data?.blocks?.find((b) => b.type === "table");
  ok("substitutions table present", !!table && table.rows.length > 3, JSON.stringify(table?.rows?.length));
  ok("substitutions table header rows", (table?.headerRows ?? 0) >= 1, String(table?.headerRows));
  ok(
    "substitutions cells keep colspan",
    !!table && table.rows[0].every((c) => Number.isInteger(c.colspan) && c.colspan >= 1),
  );

  const denied = await req("GET", "/substitutions");
  ok("substitutions requires auth 401", denied.status === 401, String(denied.status));
}

console.log("== schedule ==");
{
  const groups = await req("GET", "/schedule/groups", { token: studentToken });
  ok(
    "schedule groups visible",
    groups.status === 200 && groups.data.groups.some((g) => g.id === groupId),
    `${groups.status} ${JSON.stringify(groups.data)?.slice(0, 160)}`,
  );

  const noGroup = await req("GET", "/schedule", { token: studentToken });
  ok("schedule requires group 400", noGroup.status === 400, String(noGroup.status));

  const own = await req("GET", `/schedule?groupId=${groupId}&week=upper`, { token: studentToken });
  ok("student sees own schedule 200", own.status === 200, String(own.status));
  ok("own group flag", own.data?.isOwnGroup === true);
  ok("student cannot edit schedule", own.data?.canEdit === false);
  ok("student can view own homework", own.data?.canViewHomework === true);
  ok("new schedule is empty", Array.isArray(own.data?.entries) && own.data.entries.length === 0);

  const deniedCreate = await req("POST", "/schedule", {
    token: studentToken,
    body: { groupId, subjectId, day: 1, pairNumber: 1, weekType: "all" },
  });
  ok("student cannot create pair 403", deniedCreate.status === 403, String(deniedCreate.status));

  const headmanCreate = await req("POST", "/schedule", {
    token: headmanToken,
    body: { groupId, subjectId, day: 1, pairNumber: 1, weekType: "all" },
  });
  ok("headman cannot create pair 403", headmanCreate.status === 403, String(headmanCreate.status));

  const badColor = await req("POST", "/schedule", {
    token: admin,
    body: { groupId, subjectId, day: 1, pairNumber: 1, color: "red" },
  });
  ok("bad pair color rejected 400", badColor.status === 400, String(badColor.status));

  const badDay = await req("POST", "/schedule", {
    token: admin,
    body: { groupId, subjectId, day: 9, pairNumber: 1 },
  });
  ok("bad day rejected 400", badDay.status === 400, String(badDay.status));

  const created = await req("POST", "/schedule", {
    token: admin,
    body: {
      groupId,
      subjectId,
      day: 1,
      pairNumber: 1,
      weekType: "all",
      room: "101",
      color: "#B3261E",
      note: "Пол пары",
    },
  });
  ok("admin creates pair 201", created.status === 201, `${created.status} ${JSON.stringify(created.data)}`);
  scheduleId = created.data?.id;

  const duplicate = await req("POST", "/schedule", {
    token: admin,
    body: { groupId, subjectId, day: 1, pairNumber: 1, weekType: "all" },
  });
  ok("duplicate slot 409", duplicate.status === 409, String(duplicate.status));

  const teacherUpdate = await req("PATCH", `/schedule/${scheduleId}`, {
    token: teacherToken,
    body: { day: 2 },
  });
  ok("teacher cannot edit schedule 403", teacherUpdate.status === 403, String(teacherUpdate.status));

  const headmanUpdate = await req("PATCH", `/schedule/${scheduleId}`, {
    token: headmanToken,
    body: { day: 2 },
  });
  ok("headman cannot edit schedule 403", headmanUpdate.status === 403, String(headmanUpdate.status));

  const updated = await req("PATCH", `/schedule/${scheduleId}`, {
    token: admin,
    body: { day: 2, pairNumber: 2, weekType: "upper", room: "202", color: "#2F6B5B" },
  });
  ok("admin updates pair 200", updated.status === 200, String(updated.status));

  const upper = await req("GET", `/schedule?groupId=${groupId}&week=upper`, { token: studentToken });
  const entry = upper.data?.entries?.find((e) => e.id === scheduleId);
  ok("pair visible in upper week", !!entry, JSON.stringify(upper.data?.entries));
  ok("pair color saved", entry?.color === "#2F6B5B", String(entry?.color));
  ok("pair moved to day 2 pair 2", entry?.day === 2 && entry?.pairNumber === 2);
  ok("pair room saved", entry?.room === "202", String(entry?.room));

  const clearColor = await req("PATCH", `/schedule/${scheduleId}`, {
    token: admin,
    body: { color: "" },
  });
  ok("empty color clears pair 200", clearColor.status === 200, String(clearColor.status));
  const afterClear = await req("GET", `/schedule?groupId=${groupId}&week=upper`, {
    token: studentToken,
  });
  const clearedEntry = afterClear.data?.entries?.find((e) => e.id === scheduleId);
  ok("pair color cleared", clearedEntry?.color === null, String(clearedEntry?.color));

  const lower = await req("GET", `/schedule?groupId=${groupId}&week=lower`, { token: studentToken });
  ok(
    "upper-only pair hidden in lower week",
    !lower.data?.entries?.some((e) => e.id === scheduleId),
  );

  // Время пар можно переопределить для конкретного дня (короткая суббота).
  const studentTimes = await req("PUT", "/schedule/times", {
    token: studentToken,
    body: {
      groupId,
      day: 6,
      entries: [{ pairNumber: 1, startTime: "09:00", endTime: "10:20" }],
    },
  });
  ok("student cannot edit pair times 403", studentTimes.status === 403, String(studentTimes.status));

  const badTime = await req("PUT", "/schedule/times", {
    token: admin,
    body: {
      groupId,
      day: 6,
      entries: [{ pairNumber: 1, startTime: "9:5", endTime: "10:20" }],
    },
  });
  ok("bad pair time rejected 400", badTime.status === 400, String(badTime.status));

  const backwardsTime = await req("PUT", "/schedule/times", {
    token: admin,
    body: {
      groupId,
      day: 6,
      entries: [{ pairNumber: 1, startTime: "11:00", endTime: "10:00" }],
    },
  });
  ok("backwards pair time rejected 400", backwardsTime.status === 400, String(backwardsTime.status));

  const savedTimes = await req("PUT", "/schedule/times", {
    token: admin,
    body: {
      groupId,
      day: 6,
      entries: [
        { pairNumber: 1, startTime: "9:00", endTime: "10:20" },
        { pairNumber: 2, startTime: "10:30", endTime: "11:50" },
      ],
    },
  });
  ok("admin saves pair times 200", savedTimes.status === 200, String(savedTimes.status));

  const withTimes = await req("GET", `/schedule?groupId=${groupId}&week=upper`, {
    token: studentToken,
  });
  ok("schedule returns pair times", Array.isArray(withTimes.data?.times));
  const saturdayFirst = withTimes.data?.times?.find((t) => t.day === 6 && t.pairNumber === 1);
  ok("saturday time saved", saturdayFirst?.startTime === "09:00" && saturdayFirst?.endTime === "10:20");

  const resetTimes = await req("PUT", "/schedule/times", {
    token: admin,
    body: { groupId, day: 6, entries: [] },
  });
  ok("empty times reset day 200", resetTimes.status === 200, String(resetTimes.status));
  const afterReset = await req("GET", `/schedule?groupId=${groupId}&week=upper`, {
    token: studentToken,
  });
  ok(
    "pair times reset to default",
    !afterReset.data?.times?.some((t) => t.day === 6),
  );

  // Пара из двух предметов по 45 минут.
  const secondSubject = await req("POST", "/subjects", {
    token: admin,
    body: { name: "E2E Второй предмет", groupId, teacherId },
  });
  ok("create second subject 201", secondSubject.status === 201, `${secondSubject.status}`);
  secondSubjectId = secondSubject.data?.id;

  const twoInOne = await req("POST", "/schedule", {
    token: admin,
    body: {
      groupId,
      subjectId,
      day: 3,
      pairNumber: 5,
      weekType: "all",
      room: "204",
      color: "#345E8C",
      secondSubjectId,
      secondRoom: "305",
    },
  });
  ok("admin creates split pair 201", twoInOne.status === 201, `${twoInOne.status} ${JSON.stringify(twoInOne.data)}`);
  splitScheduleId = twoInOne.data?.id;

  const sameSubject = await req("POST", "/schedule", {
    token: admin,
    body: { groupId, subjectId, day: 4, pairNumber: 6, secondSubjectId: subjectId },
  });
  ok("split needs another subject 400", sameSubject.status === 400, String(sameSubject.status));

  const splitView = await req("GET", `/schedule?groupId=${groupId}&week=upper`, {
    token: studentToken,
  });
  const splitEntry = splitView.data?.entries?.find((e) => e.id === splitScheduleId);
  ok("split pair visible", !!splitEntry);
  ok("split flag saved", splitEntry?.split === true);
  ok("split second subject saved", splitEntry?.secondSubjectName === "E2E Второй предмет", String(splitEntry?.secondSubjectName));
  ok("split second room saved", splitEntry?.secondRoom === "305", String(splitEntry?.secondRoom));
  ok("split pair keeps color", splitEntry?.color === "#345E8C", String(splitEntry?.color));

  if (otherGroupId) {
    const foreign = await req("GET", `/schedule?groupId=${otherGroupId}&week=upper`, {
      token: studentToken,
    });
    ok("student sees other group schedule 200", foreign.status === 200, String(foreign.status));
    ok("other group schedule has no homework access", foreign.data?.canViewHomework === false);
    ok("other group schedule is read-only", foreign.data?.canEdit === false);

    const foreignSubject = await req("GET", `/subjects?groupId=${otherGroupId}`, { token: admin });
    const foreignSubjectId = foreignSubject.data?.subjects?.[0]?.id;
    if (foreignSubjectId) {
      const foreignHomework = await req(
        "GET",
        `/homework?groupId=${otherGroupId}&subjectId=${foreignSubjectId}`,
        { token: studentToken },
      );
      ok("other group homework forbidden 403", foreignHomework.status === 403, String(foreignHomework.status));
      const foreignTop = await req(
        "GET",
        `/homework/top?groupId=${otherGroupId}&period=week`,
        { token: studentToken },
      );
      ok("other group top forbidden 403", foreignTop.status === 403, String(foreignTop.status));
    }

    const foreignEdit = await req("POST", "/schedule", {
      token: headmanToken,
      body: { groupId: otherGroupId, subjectId, day: 3, pairNumber: 3 },
    });
    ok("headman cannot edit other group 403", foreignEdit.status === 403, String(foreignEdit.status));

    const foreignSubjectPair = await req("POST", "/schedule", {
      token: admin,
      body: { groupId: otherGroupId, subjectId, day: 3, pairNumber: 4 },
    });
    ok("admin cannot put foreign subject 400", foreignSubjectPair.status === 400, String(foreignSubjectPair.status));
  }
}

console.log("== homework ==");
{
  // Берём верхнюю неделю и день пары (день 2), чтобы проверить привязку ДЗ к дате.
  let upperMonday = mondayOf(new Date());
  while (weekTypeOf(upperMonday) !== "upper") upperMonday = addDays(upperMonday, 7);
  const lessonDate = isoDate(addDays(upperMonday, 1));

  const created = await req("POST", "/homework", {
    token: studentToken,
    body: {
      groupId,
      subjectId,
      text: "E2E: подготовить отчёт по лабораторной работе.",
      dueDate: "2026-12-01",
      lessonDate,
    },
  });
  ok("student posts homework 201", created.status === 201, `${created.status} ${JSON.stringify(created.data)}`);
  homeworkId = created.data?.id;

  const list = await req("GET", `/homework?groupId=${groupId}&subjectId=${subjectId}`, {
    token: studentToken,
  });
  ok("homework list 200", list.status === 200, String(list.status));
  const own = list.data?.items?.find((item) => item.id === homeworkId);
  ok("homework author recorded", own?.authorName === "E2E Обучающийся", String(own?.authorName));
  ok("homework role recorded", own?.authorRole === "student", String(own?.authorRole));
  ok("homework due date saved", own?.dueDate === "2026-12-01", String(own?.dueDate));
  ok("homework lesson date saved", own?.lessonDate === lessonDate, String(own?.lessonDate));
  ok("author can edit own homework", own?.canEdit === true);

  const datedList = await req(
    "GET",
    `/homework?groupId=${groupId}&subjectId=${subjectId}&date=${lessonDate}`,
    { token: studentToken },
  );
  ok(
    "homework filtered by lesson date",
    datedList.status === 200 && datedList.data?.items?.length === 1,
    `${datedList.status} ${datedList.data?.items?.length}`,
  );

  const otherDayList = await req(
    "GET",
    `/homework?groupId=${groupId}&subjectId=${subjectId}&date=2099-01-05`,
    { token: studentToken },
  );
  ok(
    "no homework on another date",
    otherDayList.status === 200 && otherDayList.data?.items?.length === 0,
    `${otherDayList.status} ${otherDayList.data?.items?.length}`,
  );

  const badLessonDate = await req("POST", "/homework", {
    token: studentToken,
    body: { groupId, subjectId, text: "Плохая дата занятия", lessonDate: "01.12.2026" },
  });
  ok("bad lesson date 400", badLessonDate.status === 400, String(badLessonDate.status));

  const sameWeek = await req(
    "GET",
    `/schedule?groupId=${groupId}&week=upper&weekStart=${isoDate(upperMonday)}`,
    { token: studentToken },
  );
  const datedEntry = sameWeek.data?.entries?.find((e) => e.id === scheduleId);
  ok("pair shows homework of its date", datedEntry?.homeworkCount === 1, String(datedEntry?.homeworkCount));
  ok("pair returns lesson date", datedEntry?.lessonDate === lessonDate, String(datedEntry?.lessonDate));

  let laterMonday = addDays(upperMonday, 7);
  while (weekTypeOf(laterMonday) !== "upper") laterMonday = addDays(laterMonday, 7);
  const laterWeek = await req(
    "GET",
    `/schedule?groupId=${groupId}&week=upper&weekStart=${isoDate(laterMonday)}`,
    { token: studentToken },
  );
  const laterEntry = laterWeek.data?.entries?.find((e) => e.id === scheduleId);
  ok("homework not carried to another week", laterEntry?.homeworkCount === 0, String(laterEntry?.homeworkCount));

  const badWeekStart = await req(
    "GET",
    `/schedule?groupId=${groupId}&week=upper&weekStart=not-a-date`,
    { token: studentToken },
  );
  ok("bad week start 400", badWeekStart.status === 400, String(badWeekStart.status));

  const second = await req("POST", "/homework", {
    token: headmanToken,
    body: { groupId, subjectId, text: "E2E: повторить конспект к семинару." },
  });
  ok("headman posts homework 201", second.status === 201, String(second.status));
  secondHomeworkId = second.data?.id;

  const studentEdits = await req("PATCH", `/homework/${secondHomeworkId}`, {
    token: studentToken,
    body: { text: "Подмена чужого задания" },
  });
  ok("student cannot edit another's homework 403", studentEdits.status === 403, String(studentEdits.status));

  const studentDeletes = await req("DELETE", `/homework/${secondHomeworkId}`, { token: studentToken });
  ok("student cannot delete another's homework 403", studentDeletes.status === 403, String(studentDeletes.status));

  const badDate = await req("POST", "/homework", {
    token: studentToken,
    body: { groupId, subjectId, text: "Плохая дата", dueDate: "01.12.2026" },
  });
  ok("bad homework due date 400", badDate.status === 400, String(badDate.status));

  const updated = await req("PATCH", `/homework/${homeworkId}`, {
    token: studentToken,
    body: { text: "E2E: обновлённое домашнее задание.", dueDate: null },
  });
  ok("author updates homework 200", updated.status === 200, String(updated.status));

  const clearDue = await req("PATCH", `/homework/${homeworkId}`, {
    token: studentToken,
    body: { dueDate: "" },
  });
  ok("empty due date clears homework 200", clearDue.status === 200, String(clearDue.status));
  const afterDue = await req("GET", `/homework?groupId=${groupId}&subjectId=${subjectId}`, {
    token: studentToken,
  });
  const clearedHomework = afterDue.data?.items?.find((item) => item.id === homeworkId);
  ok("homework due date cleared", clearedHomework?.dueDate === null, String(clearedHomework?.dueDate));

  const teacherSees = await req("GET", `/homework?groupId=${groupId}&subjectId=${subjectId}`, {
    token: teacherToken,
  });
  ok(
    "teacher sees group homework",
    teacherSees.status === 200 && teacherSees.data?.items?.length >= 2,
    `${teacherSees.status} ${teacherSees.data?.items?.length}`,
  );

  const moderated = await req("DELETE", `/homework/${secondHomeworkId}`, { token: headmanToken });
  ok("headman moderates homework 200", moderated.status === 200, String(moderated.status));

  const topWeek = await req("GET", `/homework/top?groupId=${groupId}&period=week`, {
    token: studentToken,
  });
  ok("homework top week 200", topWeek.status === 200, String(topWeek.status));
  ok("homework top period week", topWeek.data?.period === "week");
  ok("homework top counts entries", (topWeek.data?.total ?? 0) >= 1, String(topWeek.data?.total));
  ok(
    "homework top sorted by count",
    Array.isArray(topWeek.data?.items) &&
      topWeek.data.items.every((item, index, arr) => index === 0 || arr[index - 1].count >= item.count),
  );

  const topMonth = await req("GET", `/homework/top?groupId=${groupId}&period=month`, {
    token: studentToken,
  });
  ok("homework top month 200", topMonth.status === 200, String(topMonth.status));
  ok("homework top period month", topMonth.data?.period === "month");

  const noGroupTop = await req("GET", "/homework/top", { token: studentToken });
  ok("homework top requires group 400", noGroupTop.status === 400, String(noGroupTop.status));
}

console.log("== registration ==");
{
  const uniq = "e2e_r_" + stamp;
  const r = await req("POST", "/auth/register", {
    body: { login: uniq, password, fullName: "E2E Зарегистрированный", groupCode: testGroupCode },
  });
  ok("register with code 201", r.status === 201 && !!r.data?.token, `${r.status} ${JSON.stringify(r.data)}`);
  ok("registered role student", r.data?.user?.role === "student");

  const dup = await req("POST", "/auth/register", {
    body: { login: uniq, password, fullName: "E2E Зарегистрированный", groupCode: testGroupCode },
  });
  ok("duplicate register 409", dup.status === 409, String(dup.status));

  const badG = await req("POST", "/auth/register", {
    body: { login: uniq + "b", password, fullName: "Тест", groupCode: "WRONG" },
  });
  ok("bad group code 404", badG.status === 404, String(badG.status));

  if (r.data?.user?.id) {
    const del = await req("DELETE", `/users/${r.data.user.id}`, { token: admin });
    ok("admin deletes registered student", del.status === 200, String(del.status));
    const deletedToken = await req("GET", "/groups", { token: r.data.token });
    ok("deleted account token rejected 401", deletedToken.status === 401, String(deletedToken.status));
  }
}

console.log("== change password ==");
{
  const same = await req("POST", "/auth/change-password", {
    token: studentToken,
    body: { currentPassword: password, newPassword: password },
  });
  ok("same password rejected 400", same.status === 400, String(same.status));

  const ch = await req("POST", "/auth/change-password", {
    token: studentToken,
    body: { currentPassword: password, newPassword: password + "4" },
  });
  ok("change password ok", ch.status === 200, `${ch.status} ${JSON.stringify(ch.data)}`);

  const relogin = await req("POST", "/auth/login", { body: { login: studentLogin, password: password + "4" } });
  ok("login with new password", relogin.status === 200 && !!relogin.data?.token);

  const back = await req("POST", "/auth/change-password", {
    token: relogin.data?.token,
    body: { currentPassword: password + "4", newPassword: password },
  });
  ok("password restored", back.status === 200, String(back.status));
}

console.log("== authorization edge ==");
{
  const noToken = await req("GET", "/groups");
  ok("no token 401", noToken.status === 401, String(noToken.status));

  const fake = await req("GET", "/groups", { token: "abc.def.ghi" });
  ok("invalid token 401", fake.status === 401, String(fake.status));

  const studentUsers = await req("POST", "/users", {
    token: studentToken,
    body: { fullName: "Hack", login: "hack1", password, role: "admin" },
  });
  ok("student cannot create users 403", studentUsers.status === 403, String(studentUsers.status));

  const headmanSubject = await req("POST", "/subjects", {
    token: headmanToken,
    body: { name: "Hack subject", groupId },
  });
  ok("headman cannot manage subjects 403", headmanSubject.status === 403, String(headmanSubject.status));

  const adminLast = await req("DELETE", `/users/${adminId}`, { token: admin });
  ok("cannot delete self", adminLast.status === 400, String(adminLast.status));
}

console.log("== cleanup ==");
{
  if (scheduleId) {
    const del = await req("DELETE", `/schedule/${scheduleId}`, { token: admin });
    ok("cleanup schedule", del.status === 200, String(del.status));
  }
  if (splitScheduleId) {
    const del = await req("DELETE", `/schedule/${splitScheduleId}`, { token: admin });
    ok("cleanup split schedule", del.status === 200, String(del.status));
  }
  if (homeworkId) {
    const del = await req("DELETE", `/homework/${homeworkId}`, { token: admin });
    ok("cleanup homework", del.status === 200, String(del.status));
  }
  for (const id of cleanupIds()) {
    const del = await req("DELETE", `/users/${id}`, { token: admin });
    ok(`cleanup user ${id}`, del.status === 200, String(del.status));
  }
  if (groupId) {
    const del = await req("DELETE", `/groups/${groupId}`, { token: admin });
    ok("cleanup group", del.status === 200, String(del.status));
    const gone = await req("GET", "/groups", { token: admin });
    ok("test group removed", !gone.data?.groups?.some((g) => g.id === groupId));
  }
}

console.log(`\nRESULT: ${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);

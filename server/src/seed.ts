import bcrypt from 'bcryptjs';
import { get, run, transaction } from './db.js';

function isoDaysAgo(days: number): string {
  const date = new Date();
  date.setHours(12, 0, 0, 0);
  date.setDate(date.getDate() - days);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function recentWeekdays(daysBack: number): string[] {
  const result: string[] = [];
  for (let offset = daysBack; offset >= 1; offset -= 1) {
    const date = new Date();
    date.setHours(12, 0, 0, 0);
    date.setDate(date.getDate() - offset);
    const weekday = date.getDay();
    if (weekday === 0 || weekday === 6) continue;
    result.push(isoDaysAgo(offset));
  }
  return result;
}

let randomState = 20260214;
function random(): number {
  randomState = (randomState * 1664525 + 1013904223) % 4294967296;
  return randomState / 4294967296;
}

const existing = get<{ id: number }>("SELECT id FROM groups WHERE code = 'IS21'");
if (existing) {
  console.log('[seed:demo] Демо-группа IS21 уже существует — пропускаю.');
  process.exit(0);
}

const students = [
  ['Соколов Артём', 'sokolov'],
  ['Петрова Мария', 'petrova'],
  ['Кузнецов Даниил', 'kuznetsov'],
  ['Волкова Софья', 'volkova'],
  ['Морозов Кирилл', 'morozov'],
  ['Новикова Алиса', 'novikova'],
  ['Фёдоров Максим', 'fedorov'],
  ['Егорова Полина', 'egorova'],
  ['Захаров Илья', 'zaharov'],
  ['Михайлова Варвара', 'mihaylova'],
  ['Орлов Никита', 'orlov'],
  ['Смирнова Ева', 'smirnova.e'],
] as const;

const teachers = [
  ['Иванова Мария Сергеевна', 'ivanova'],
  ['Смирнов Алексей Петрович', 'smirnov'],
] as const;

const subjects = [
  ['Математика', 0],
  ['Информатика', 1],
  ['История', 0],
  ['Русский язык', 1],
] as const;

const teacherHash = bcrypt.hashSync('teacher123', 10);
const studentHash = bcrypt.hashSync('student123', 10);
const headmanHash = bcrypt.hashSync('headman123', 10);

const groupId = transaction(() => {
  const group = run(
    'INSERT INTO groups (name, code, avatar) VALUES (?, ?, ?)',
    'ИС-21',
    'IS21',
    '🎓',
  );

  run(
    `INSERT INTO users (login, password_hash, role, full_name, group_id, avatar)
     VALUES (?, ?, 'headman', ?, ?, ?)`,
    'starosta',
    headmanHash,
    'Староста ИС-21',
    group.lastInsertRowid,
    '⭐',
  );
  const headmanId = get<{ id: number }>(
    "SELECT id FROM users WHERE login = 'starosta'",
  )?.id;

  const teacherIds = teachers.map(([fullName, login]) =>
    run(
      `INSERT INTO users (login, password_hash, role, full_name)
       VALUES (?, ?, 'teacher', ?)`,
      login,
      teacherHash,
      fullName,
    ).lastInsertRowid,
  );

  const studentIds = students.map(([fullName, login]) =>
    run(
      `INSERT INTO users (login, password_hash, role, full_name, group_id)
       VALUES (?, ?, 'student', ?, ?)`,
      login,
      studentHash,
      fullName,
      group.lastInsertRowid,
    ).lastInsertRowid,
  );

  const weekdays = recentWeekdays(28);
  const subjectIds = subjects.map(([name, teacherIndex], index) => {
    const subject = run(
      'INSERT INTO subjects (name, group_id, teacher_id) VALUES (?, ?, ?)',
      name,
      group.lastInsertRowid,
      teacherIds[teacherIndex],
    );
    const subjectId = subject.lastInsertRowid;

    const lessonDates: string[] = [];
    const stride = Math.max(1, Math.floor(weekdays.length / 7));
    for (let k = 0; k < 6; k += 1) {
      const position = Math.min(weekdays.length - 1, 1 + index + k * stride);
      lessonDates.push(weekdays[position]!);
    }

    lessonDates.forEach((date, lessonIndex) => {
      const lesson = run(
        'INSERT INTO lessons (subject_id, date, pair_number, topic) VALUES (?, ?, ?, ?)',
        subjectId,
        date,
        ((index + lessonIndex) % 4) + 1,
        `${name}: тема ${lessonIndex + 1}`,
      );

      for (const studentId of studentIds) {
        const roll = random();
        if (roll < 0.09) {
          const reasons = ['Болезнь', 'Семейные обстоятельства', 'Соревнования', ''];
          const reason = reasons[Math.floor(random() * reasons.length)] ?? '';
          run(
            'INSERT INTO records (lesson_id, student_id, absent, reason) VALUES (?, ?, 1, ?)',
            lesson.lastInsertRowid,
            studentId,
            reason || null,
          );
        } else if (roll < 0.88) {
          const grade = 4 + Math.floor(random() * 7);
          run(
            'INSERT INTO records (lesson_id, student_id, grade) VALUES (?, ?, ?)',
            lesson.lastInsertRowid,
            studentId,
            grade,
          );
        }
      }
    });

    return subjectId;
  });

  return { id: group.lastInsertRowid, subjects: subjectIds, students: studentIds, headmanId };
});

// Демо-расписание: пары по дням, верхняя/нижняя неделя, цвета для выделения.
const scheduleSlots: Array<[number, number, number, 'all' | 'upper' | 'lower', string, string]> = [
  [1, 1, 0, 'all', '#2F6B5B', '204'],
  [1, 2, 1, 'all', '#345E8C', '310'],
  [1, 3, 2, 'lower', '#C08135', '118'],
  [2, 1, 3, 'all', '#7B4B6B', '207'],
  [2, 2, 0, 'all', '#2F6B5B', '204'],
  [3, 1, 1, 'upper', '#345E8C', '310'],
  [3, 2, 2, 'all', '#C08135', '118'],
  [4, 1, 3, 'all', '#7B4B6B', '207'],
  [4, 2, 0, 'lower', '#B3261E', '204'],
  [5, 1, 1, 'all', '#2F6B5B', '310'],
];

for (const [day, pair, subjectIndex, weekType, color, room] of scheduleSlots) {
  run(
    `INSERT INTO schedule (group_id, subject_id, day, pair_number, week_type, room, color)
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
    groupId.id,
    groupId.subjects[subjectIndex]!,
    day,
    pair,
    weekType,
    room,
    color,
  );
}

// Одна пара разделена на два предмета по 45 минут — так выглядит «двойная» ячейка.
run(
  `UPDATE schedule
   SET second_subject_id = ?, second_room = ?, note = ?
   WHERE group_id = ? AND day = 5 AND pair_number = 1`,
  groupId.subjects[2]!,
  '310',
  '2 × 45 минут',
  groupId.id,
);

// Короткая суббота: обед 20 минут вместо 40.
const saturdayTimes: Array<[string, string]> = [
  ['08:00', '09:40'],
  ['09:50', '11:30'],
  ['11:50', '13:30'],
  ['13:40', '15:20'],
  ['15:30', '17:10'],
  ['17:20', '19:00'],
  ['19:10', '20:50'],
  ['21:00', '22:40'],
];

saturdayTimes.forEach(([start, end], index) => {
  run(
    `INSERT INTO schedule_times (group_id, day, pair_number, start_time, end_time)
     VALUES (?, 6, ?, ?, ?)`,
    groupId.id,
    index + 1,
    start,
    end,
  );
});

// ДЗ закрепляется за конкретным днём: на новой неделе оно не «переезжает» вперёд.
const demoHomework: Array<[number, string, number | undefined, number, number]> = [
  [0, '§ 12–14, задачи 3, 5 и 7 письменно. Принести тетрадь с решением.', 0, 2, 1],
  [1, 'Написать программу сортировки массива, загрузить файл в общую папку.', 1, 1, 5],
  [3, 'Сочинение «Мой город» — 1,5 страницы, сдать в пятницу.', undefined, 4, 2],
];

function isoDayOfCurrentWeek(day: number): string {
  const now = new Date();
  const mondayOffset = (now.getUTCDay() + 6) % 7;
  const date = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - mondayOffset + day - 1),
  );
  return date.toISOString().slice(0, 10);
}

for (const [subjectIndex, text, studentIndex, daysAgo, day] of demoHomework) {
  const authorId = studentIndex === undefined
    ? groupId.headmanId ?? groupId.students[0]
    : groupId.students[studentIndex];
  const author = get<{ full_name: string; role: string }>(
    'SELECT full_name, role FROM users WHERE id = ?',
    authorId,
  );
  run(
    `INSERT INTO homework
       (group_id, subject_id, author_id, author_name, author_role, text, lesson_date, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now', ?))`,
    groupId.id,
    groupId.subjects[subjectIndex]!,
    authorId,
    author?.full_name ?? 'Обучающийся',
    author?.role ?? 'student',
    text,
    isoDayOfCurrentWeek(day),
    `-${daysAgo} days`,
  );
}

console.log('');
console.log('  Демо-данные готовы');
console.log(`  Группа: ИС-21, код для регистрации: IS21 (${students.length} обучающихся)`);
console.log(`  Предметов: ${subjects.length}, id группы: ${groupId.id}`);
console.log('');
console.log('  Аккаунты:');
console.log('    admin / admin12345      — администратор');
console.log('    ivanova / teacher123    — преподаватель');
console.log('    smirnov / teacher123    — преподаватель');
console.log('    starosta / headman123   — староста группы');
console.log(`    ${students[0][1]} / student123 — обучающийся (и остальные логины из списка)`);
console.log('');

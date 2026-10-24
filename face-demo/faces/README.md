# Enrollment photos

One folder per person, named exactly how the name should be spoken/shown
(`_` is read as a space, so `mayank_jain` is announced as "mayank jain"):

```
faces/
├── mayank_jain/
│   ├── 01.jpg
│   └── 02.jpg
└── teammate_two/
    └── ...
```

- 10 to 20 photos per person, `.jpg` / `.jpeg` / `.png`.
- Vary angle (straight, slight left/right, up/down), lighting (window, indoor, dim), expression, and with/without glasses.
- One face per photo, face clearly visible. Photos with no detectable face are skipped by `enroll.py`.
- Only add people who have consented. Everything in this folder except this file is gitignored and must never be committed.

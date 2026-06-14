# debug-logs

Drop captured Pi Drive log sessions here for review.

Get logs off the phone via **Settings → Developer settings → Diagnostics / logs → Share logs**
(or USB / `adb pull`), then unzip into this folder and commit:

```bash
unzip ~/Downloads/pidrive-logs-*.zip -d pi-drive-android/debug-logs/
git add pi-drive-android/debug-logs/
git commit -m "debug-logs: <what you were debugging> <date>"
```

See [`../DEBUGGING.md`](../DEBUGGING.md) for the full workflow and how to read the logs.

> Logs may contain your VIN, adapter MAC, and phone model. Strip those lines before committing to a
> public repo.

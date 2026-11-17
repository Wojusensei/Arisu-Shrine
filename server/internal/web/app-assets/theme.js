// 主题与正文字号提前应用：在 CSS 解析前挂 .dark / 设 --reading-scale，避免闪动。
// 放在 head 里同步执行；localStorage 在私密模式下可能抛错，整体兜住。
try {
  if (localStorage.getItem("theme") === "dark") {
    document.documentElement.classList.add("dark");
  }
  var rs = parseFloat(localStorage.getItem("readingScale"));
  // 与 ReadingScale.steps 同四档（ReadingScale.kt:21）；越界值回落到标准档
  if (rs === 0.9 || rs === 1.15 || rs === 1.3) {
    document.documentElement.style.setProperty("--reading-scale", String(rs));
  }
} catch (e) {}

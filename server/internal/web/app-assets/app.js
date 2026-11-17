// 网页端动作层（无框架）：fetch 动作、弹层、菜单、toast、心跳、面板。
// CSP script-src 'self'，本文件是唯一脚本；页面差异用 body[data-page] 分流。
(function () {
  "use strict";

  var EMOJI = ["😀","😄","😅","😂","🙂","😉","😍","🤔","😴","😭","😡","🥺","👍","👏","🙏","🎉","🔥","⭐","🌸","🍵","🎴","⛩️","❄️","🌙"];
  // 颜文字：逐字取自 Kaomoji.kt:33-110（56 枚，只收录字符颜文字）
  var KAOMOJI = [
  "（´・ω・`）",
  "(・ω・)",
  "(｡･ω･｡)",
  "(*´ω`*)",
  "( ˘ω˘ )",
  "(￣▽￣)",
  "(￣ー￣)",
  "(・∀・)",
  "(°∀°)",
  "(＾▽＾)",
  "(＾ω＾)",
  "(≧▽≦)",
  "(≧ω≦)",
  "(◕‿◕)",
  "(•‿•)",
  "(´▽`)",
  "ヽ(´▽`)/",
  "ヽ(•‿•)ノ",
  "ヽ(°〇°)ﾉ",
  "(｡･ω･)ﾉﾞ",
  "(￣▽￣)ノ",
  "(´・ω・)ﾉ",
  "(´・ω・`)ノ",
  "(ノ°∀°)ノ",
  "(ﾉ≧∀≦)ﾉ",
  "(*ﾉωﾉ)",
  "(〃ω〃)",
  "(´,,•ω•,,`)",
  "(๑´ω`๑)",
  "(๑•́ω•̀๑)",
  "(๑˃̵ᴗ˂̵)",
  "(๑•̀ㅂ•́)و",
  "(ง •̀_•́)ง",
  "(｀・ω・´)",
  "(｀・ω・´)ゞ",
  "(｡•́︿•̀｡)",
  "(｡•́ω•̀｡)",
  "(´；ω；`)",
  "(；ω；)",
  "(╥﹏╥)",
  "(ノ_<。)",
  "(´-﹏-`)",
  "(｡╯︵╰｡)",
  "(￣^￣)",
  "(￣ヘ￣)",
  "(¬‿¬)",
  "(・_・;)",
  "(・・;)",
  "(°ー°〃)",
  "(＠_＠)",
  "(°ロ°) !",
  "(¯口¯)",
  "(・へ・)",
  "(´⊙ω⊙`)",
  "(´-ω-`)",
  "(。・ω・。)"
  ];

  // ── 基础 API：全部走同源 cookie；非 2xx 抛中文错误 ─────────────
  window.api = async function (method, path, body) {
    var opt = { method: method, headers: {} };
    if (body !== undefined) {
      opt.headers["Content-Type"] = "application/json";
      opt.body = JSON.stringify(body);
    }
    var res = await fetch(path, opt);
    var data = null;
    try { data = await res.json(); } catch (e) { /* 空体 */ }
    if (!res.ok) {
      var msg = (data && data.error) || "网络请求失败（HTTP " + res.status + "）";
      throw new Error(msg);
    }
    return data;
  };

  // ── toast：ShrineNoticeBar 口径，底部浮出 2.2s ─────────────────
  var toastTimer = null;
  window.toast = function (msg) {
    var bar = document.querySelector(".toast");
    if (!bar) {
      bar = document.createElement("div");
      bar.className = "toast";
      document.body.appendChild(bar);
    }
    bar.textContent = msg;
    bar.classList.add("show");
    clearTimeout(toastTimer);
    // 自动消失 3000ms（States.kt:401 autoDismissMillis）——此前 2200 偏快
    toastTimer = setTimeout(function () { bar.classList.remove("show"); }, 3000);
  };

  // ── 只弹说明的条目（安卓 onNote(...)：客房/设置的死行都有这一手）──
  document.addEventListener("click", function (ev) {
    var el = ev.target.closest ? ev.target.closest("[data-note]") : null;
    if (!el) return;
    ev.preventDefault();
    window.toast(el.getAttribute("data-note"));
  });

  // ── 确认弹窗（ArisuConfirmDialog 口径：防抖 + 可选理由 + 错误内显）──
  window.confirmDialog = function (opt) {
    return new Promise(function (resolve) {
      var overlay = document.createElement("div");
      overlay.className = "dialog-overlay";
      var reasonHTML = opt.reasonHint
        ? '<div class="form-row"><span class="form-label">' + esc(opt.reasonHint) + '</span>' +
          '<textarea class="field" data-role="reason" maxlength="' + (opt.reasonMax || 500) + '" rows="2"></textarea></div>'
        : "";
      overlay.innerHTML =
        '<div class="dialog neu-surface">' +
        '<h3 class="dialog-title">' + esc(opt.title) + "</h3>" +
        (opt.body ? '<p class="dialog-body">' + esc(opt.body) + "</p>" : "") +
        reasonHTML +
        '<p class="form-error" data-role="err" style="display:none"></p>' +
        '<div class="dialog-actions">' +
        '<button class="btn-ghost" data-role="cancel">' + esc(opt.cancelText || "取消") + '</button>' +
        '<button class="btn" data-role="ok"' + (opt.danger ? ' data-danger' : '') + ">" + esc(opt.confirm || "确认") + "</button>" +
        "</div></div>";
      document.body.appendChild(overlay);
      var q = function (r) { return overlay.querySelector('[data-role="' + r + '"]'); };
      var busy = false;
      function close(v) { overlay.remove(); resolve(v); }
      q("cancel").addEventListener("click", function () {
        // onCancel：取消侧也要办事的场景（如「重新开始」要清掉草稿）
        if (opt.onCancel) { try { opt.onCancel(); } catch (e) {} }
        close(null);
      });
      overlay.addEventListener("click", function (e) { if (e.target === overlay) close(null); });
      q("ok").addEventListener("click", function () {
        if (busy) return;
        var reason = "";
        var rEl = overlay.querySelector('[data-role="reason"]');
        if (rEl) reason = rEl.value.trim();
        // reasonOptional：理由选填的弹窗（如举报处置的附言）空理由也放行
        if (opt.reasonHint && !opt.reasonOptional && !reason) {
          var e1 = q("err"); e1.textContent = opt.emptyReasonError || "理由必填"; e1.style.display = "block";
          return;
        }
        busy = true;
        q("ok").disabled = true;
        Promise.resolve()
          .then(function () { return opt.onConfirm(reason); })
          .then(function () { close(true); })
          .catch(function (e) {
            busy = false;
            q("ok").disabled = false;
            var e2 = q("err"); e2.textContent = e.message || "操作失败"; e2.style.display = "block";
          });
      });
      var first = overlay.querySelector("input,textarea");
      if (first) first.focus();
    });
  };

  function esc(s) {
    return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;")
      .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }
  window.escHTML = esc;

  // ── 下拉菜单：锚点元素下方弹出，点外关闭 ─────────────────────
  window.shrineMenu = function (anchor, items) {
    closeMenu();
    var menu = document.createElement("div");
    menu.className = "menu";
    items.forEach(function (it) {
      var b = document.createElement("button");
      b.className = "menu-item" + (it.danger ? " danger" : "");
      b.textContent = it.label;
      if (it.disabled) b.disabled = true;
      b.addEventListener("click", function () { closeMenu(); it.onClick(); });
      menu.appendChild(b);
    });
    document.body.appendChild(menu);
    var r = anchor.getBoundingClientRect();
    menu.style.top = r.bottom + 6 + window.scrollY + "px";
    menu.style.left = Math.min(r.left, window.innerWidth - menu.offsetWidth - 8) + "px";
    setTimeout(function () {
      document.addEventListener("click", function h(e) {
        if (!menu.contains(e.target)) { closeMenu(); document.removeEventListener("click", h); }
      });
    }, 0);
  };
  var currentMenu = null;
  function closeMenu() { if (currentMenu) { currentMenu.remove(); currentMenu = null; } }

  // ── 底部 sheet：上滑淡入，点遮罩关 ────────────────────────────
  window.shrineSheet = function (innerHTML) {
    var overlay = document.createElement("div");
    overlay.className = "sheet-overlay";
    overlay.innerHTML = '<div class="sheet">' + innerHTML + "</div>";
    document.body.appendChild(overlay);
    overlay.addEventListener("click", function (e) {
      if (e.target === overlay) overlay.remove();
    });
    return overlay;
  };
  window.closeSheets = function () {
    document.querySelectorAll(".sheet-overlay").forEach(function (o) { o.remove(); });
  };

  // ── 声明式动作：data-api="方法 路径" + data-body(JSON) + data-confirm(JSON) ──
  // 成功默认 location.reload()；data-toast 有值则只 toast 不刷新
  document.addEventListener("click", function (e) {
    var el = e.target.closest("[data-api]");
    if (!el || el.disabled) return;
    e.preventDefault();
    var spec = el.getAttribute("data-api").split(" ");
    var body = null;
    var raw = el.getAttribute("data-body");
    if (raw) { try { body = JSON.parse(raw); } catch (err) { /* 忽略 */ } }
    var conf = el.getAttribute("data-confirm");
    var doIt = function () {
      el.disabled = true;
      window.api(spec[0], spec[1], body)
        .then(function (data) {
          var t = el.getAttribute("data-toast");
          if (t) { toast(t); if (el.getAttribute("data-noreload")) return; location.reload(); }
          else if (el.getAttribute("data-noreload") && el.getAttribute("data-apply")) {
            window[el.getAttribute("data-apply")](data, el);
          } else { location.reload(); }
        })
        .catch(function (err) { toast(err.message); el.disabled = false; });
    };
    if (conf) {
      var c = JSON.parse(conf);
      window.confirmDialog({
        title: c.title, body: c.body || "", reasonHint: c.reasonHint || null,
        confirm: c.confirm || "确认", danger: !!c.danger,
        onConfirm: function (reason) {
          if (c.reasonField && body) body[c.reasonField] = reason;
          return window.api(spec[0], spec[1], body);
        },
      }).then(function (ok) { if (ok && el.getAttribute("data-after") === "back") history.back(); });
    } else {
      doIt();
    }
  });

  // ── 心跳：60s 保活 + 未读角标随跳 ──────────────────────────────
  function beat() {
    if (!document.body.dataset.page || document.body.dataset.page === "auth") return;
    window.api("POST", "/api/me/heartbeat", { active_seconds: 60 })
      .then(function (res) {
        if (res.unread !== undefined && res.unread !== null) {
          document.querySelectorAll("[data-unread-badge]").forEach(function (b) {
            var n = res.unread;
            if (n > 0) { b.textContent = n > 99 ? "99+" : n; b.style.display = "flex"; }
            else b.style.display = "none";
          });
        }
      })
      .catch(function () { /* 心跳失败不打扰 */ });
  }
  if (document.body.dataset.page) {
    beat();
    setInterval(beat, 60000);
  }

  // ── 显码眼睛 + 活跃提交钮 ─────────────────────────────────────
  document.querySelectorAll("button[data-eye]").forEach(function (btn) {
    btn.addEventListener("click", function () {
      var input = document.getElementById(btn.getAttribute("data-eye"));
      if (!input) return;
      var show = input.type === "password";
      input.type = show ? "text" : "password";
      btn.innerHTML = show ? EYE_OFF_SVG : EYE_SVG;
    });
  });
  var EYE_SVG = '<svg class="ic" viewBox="0 0 24 24"><path d="M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z"/></svg>';
  var EYE_OFF_SVG = '<svg class="ic" viewBox="0 0 24 24"><path d="M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.35 4.38-.97l2.23 2.23 1.42-1.42L4.27 2.86 2 4.27z"/></svg>';

  // 活跃提交钮：表单必填项没填齐就保持浅色禁用态（对齐 ArisuPrimaryButton）
  document.querySelectorAll("form[data-live-submit]").forEach(function (form) {
    var btn = form.querySelector('button[type="submit"]:not([formaction])');
    if (!btn) return;
    var sync = function () {
      var ok = Array.prototype.every.call(form.querySelectorAll("[required]"), function (f) {
        return f.value.trim() !== "";
      });
      btn.disabled = !ok;
    };
    form.addEventListener("input", sync);
    sync();
  });

  // 两次密码一致（提交前拦一道）
  document.querySelectorAll("form[data-confirm-pw]").forEach(function (form) {
    form.addEventListener("submit", function (ev) {
      var a = form.querySelector("input[name=password]");
      var b = form.querySelector("input[name=password_confirm]");
      var box = form.querySelector(".pw-mismatch");
      if (!a || !b || !box) return;
      if (a.value !== b.value) {
        ev.preventDefault();
        box.textContent = "两次输入的密码不一致";
        box.style.display = "block";
        b.focus();
      } else box.style.display = "none";
    });
  });

  // 发送验证码（AuthScreens.kt:644-690）：等待时按钮里是静止阴阳玉，成功后
  // 60 秒倒计时、按钮置灰；全程原地更新，昵称/密码不因重渲染丢失
  document.querySelectorAll('[data-role="send-code"]').forEach(function (btn) {
    var form = btn.form;
    if (!form) return;
    var emailEl = form.querySelector('input[name="email"]');
    var noteEl = document.querySelector('[data-role="code-note"]');
    var errEl = document.querySelector('[data-role="code-err"]');
    var label = btn.textContent.trim();
    var busy = false, left = 0, timer = null;

    function paint() {
      if (busy) {
        // 阴阳玉 16dp，静止不转（安卓 YinYangOrb(rotating = false)）
        btn.innerHTML = '<svg class="ic code-orb" viewBox="0 0 22 22">' +
          '<circle cx="11" cy="11" r="10.615" style="fill:none;stroke:var(--hairline-soft);stroke-width:.77"/>' +
          '<circle cx="11" cy="11" r="10.23" style="fill:var(--neu-inset)"/>' +
          '<path d="M.77 11A10.23 10.23 0 0 1 21.23 11Z"/>' +
          '<circle cx="5.885" cy="11" r="5.115"/>' +
          '<circle cx="16.115" cy="11" r="5.115" style="fill:var(--neu-inset)"/>' +
          '<circle cx="5.885" cy="11" r="1.705" style="fill:var(--neu-inset)"/>' +
          '<circle cx="16.115" cy="11" r="1.705"/></svg>';
        btn.disabled = true;
        return;
      }
      btn.textContent = left > 0 ? left + "s" : label;
      btn.disabled = left > 0;
    }
    function tick() {
      left -= 1;
      if (left <= 0) { left = 0; clearInterval(timer); timer = null; }
      paint();
    }

    btn.addEventListener("click", function (ev) {
      ev.preventDefault();
      if (busy || left > 0) return;
      var mail = (emailEl && emailEl.value || "").trim();
      if (noteEl) { noteEl.hidden = true; noteEl.textContent = ""; }
      if (errEl) { errEl.hidden = true; errEl.textContent = ""; }
      busy = true; paint();
      var body = new URLSearchParams();
      body.set("email", mail);
      fetch(btn.getAttribute("formaction"), {
        method: "POST",
        headers: { "X-Requested-With": "fetch", "Content-Type": "application/x-www-form-urlencoded" },
        body: body.toString(),
      }).then(function (r) { return r.json(); }).then(function (r) {
        busy = false;
        if (r.error) {
          if (errEl) { errEl.textContent = r.error; errEl.hidden = false; }
          paint();
          return;
        }
        if (noteEl) { noteEl.textContent = r.notice || ""; noteEl.hidden = !r.notice; }
        left = 60; paint();
        timer = setInterval(tick, 1000);
      }).catch(function () {
        busy = false;
        if (errEl) { errEl.textContent = "寄送失败，请稍后再试"; errEl.hidden = false; }
        paint();
      });
    });
  });

  // 表情面板「设置」格的工具图标（Material Rounded Handyman 简形：扳手 + 锤）
  var SET_ICON = '<svg class="ic" viewBox="0 0 24 24"><path d="M21.67 18.17l-5.3-5.3h-.99l-2.54 2.54v.99l5.3 5.3c.39.39 1.02.39 1.41 0l2.12-2.12c.39-.39.39-1.02 0-1.41zM17.34 10.19l1.41-1.41 2.12 2.12c1.17-1.17 1.17-3.07 0-4.24l-3.54-3.54-1.41 1.41V1.71l-.7-.71-3.54 3.54.71.71h2.83l-1.41 1.41 1.06 1.06-2.89 2.89-4.13-4.13V5.06L4.83 2.04 2 4.87 5.03 7.9h1.41l4.13 4.13-.85.85H7.6l-5.3 5.3c-.39.39-.39 1.02 0 1.41l2.12 2.12c.39.39 1.02.39 1.41 0l5.3-5.3v-2.12l5.15-5.15 1.06 1.05z"/></svg>';
  // ── 表情/颜文字/贴纸面板（EmojiSheet 三 tab 口径） ────────────
  window.openEmojiSheet = function (insertFn, opts) {
    opts = opts || {};
    var tabs = [
      { key: "emoji", label: "表情" },
      { key: "kaomoji", label: "颜文字" },
    ];
    if (opts.stickers !== false) tabs.push({ key: "sticker", label: "表情包" });
    var overlay = window.shrineSheet(
      '<div class="emoji-sheet">' +
      '<div class="emoji-tabs">' + tabs.map(function (t, i) {
        return '<button class="etab' + (i === 0 ? " on" : "") + '" data-t="' + t.key + '">' + t.label + "</button>";
      }).join("") + "</div>" +
      '<div class="emoji-body" data-role="body"></div>' +
      "</div>"
    );
    var body = overlay.querySelector('[data-role="body"]');
    function render(key) {
      overlay.querySelectorAll(".etab").forEach(function (b) { b.classList.toggle("on", b.dataset.t === key); });
      if (key === "emoji") {
        body.innerHTML = '<div class="emoji-grid">' + EMOJI.map(function (e) {
          return '<button class="ecell" data-v="' + e + '">' + e + "</button>";
        }).join("") + "</div>";
      } else if (key === "kaomoji") {
        body.innerHTML = '<div class="kaomoji-grid">' + KAOMOJI.map(function (k) {
          return '<button class="kcell" data-v="' + esc(k) + '">' + esc(k) + "</button>";
        }).join("") + "</div>";
      } else {
        body.innerHTML = '<div class="sticker-loading">读取中…</div>';
        window.api("GET", "/api/stickers").then(function (res) {
          var items = res.items || [];
          // 面板最后一格是「设置」格（StickerPanel.kt:212-236：20dp 图标 + 3 + 「设置」），
          // 点它进表情包设置页（安卓是工具图标，网页用同一格子承载）
          var settingsCell = '<a class="sticker-cell stk-set" href="/me/stickers" aria-label="表情包设置">' +
            '<span class="add-ic">' + SET_ICON + '</span><span class="add-lb">设置</span></a>';
          // 头部（StickerPanel.kt:78-101）：「自留表情」titleMedium + 弹性 + 「N/上限」
          // labelSmall；4dp → 状态提示（满额换句）；12dp → 网格。
          var LIMIT = 64;
          var head = '<div class="stk-head"><b>自留表情</b><span class="stk-num">' +
            items.length + '/' + LIMIT + '</span></div>' +
            '<p class="stk-hint">' + (items.length >= LIMIT
              ? '表情夹满了，长按一张可以删'
              : '点选即插入，长按一张可以删') + '</p>';
          if (!items.length) {
            body.innerHTML = head +
              '<p class="stk-empty">还没有收藏的表情。点第一个格子进设置，从相册选一张；' +
              '长按别人发过的图也能收进来。</p>' +
              '<div class="sticker-grid">' + settingsCell + '</div>';
            return;
          }
          body.innerHTML = head +
            '<div class="sticker-grid">' + items.map(function (st) {
            return '<button class="sticker-cell" data-v="' + esc(st.url) + '"><img src="' + esc(st.url) + '" alt="表情" loading="lazy"></button>';
          }).join("") + settingsCell + "</div>";
        }).catch(function (e) { body.innerHTML = '<p class="page-end">' + esc(e.message) + "</p>"; });
      }
    }
    render("emoji");
    overlay.querySelectorAll(".etab").forEach(function (b) {
      b.addEventListener("click", function () { render(b.dataset.t); });
    });
    body.addEventListener("click", function (e) {
      var cell = e.target.closest("[data-v]");
      if (!cell) return;
      insertFn(cell.getAttribute("data-v"));
      overlay.remove();
    });
  };

  // ── 看图（ImageViewer.kt:200-270）───────────────────────────
  // 安卓：全屏 scrim + 图；底部一行提示（双击放大 · 长按保存 · 单击关闭），
  // 点一下弹出 240dp 宽的菜单：保存原图到相册 / 添加到表情包 / 收起。
  // 结果提示从下方浮出（浮在暗色 scrim 上，恒白不随主题）。
  window.openImageViewer = function (url, opts) {
    opts = opts || {};
    var overlay = document.createElement("div");
    overlay.className = "img-viewer";
    overlay.innerHTML =
      '<img src="' + esc(url) + '" alt="查看图片">' +
      '<div class="iv-bottom">' +
      '<p class="iv-notice" data-a="notice" hidden></p>' +
      '<p class="iv-hint" data-a="hint">双击放大 · 长按保存 · 单击关闭</p>' +
      '<div class="iv-menu" data-a="menu" hidden>' +
      '<button class="iv-row" data-a="save">保存原图到相册</button>' +
      (opts.noSticker ? "" : '<button class="iv-row" data-a="sticker">添加到表情包</button>') +
      '<button class="iv-row" data-a="close">收起</button>' +
      "</div>" +
      "</div>";
    document.body.appendChild(overlay);

    var img = overlay.querySelector("img");
    var hintEl = overlay.querySelector('[data-a="hint"]');
    var noticeEl = overlay.querySelector('[data-a="notice"]');
    var menuEl = overlay.querySelector('[data-a="menu"]');
    var menuOpen = false, zoom = 1, noticeTimer = null;

    function notice(text) {
      noticeEl.textContent = text;
      noticeEl.hidden = false;
      clearTimeout(noticeTimer);
      noticeTimer = setTimeout(function () { noticeEl.hidden = true; }, 2000);
    }
    function syncHint() {
      hintEl.textContent = zoom > 1
        ? "单击回正 · 双指缩放 · 长按保存"
        : "双击缩放 · 双击放大 · 长按保存 · 单击关闭".replace("双击缩放 · ", "");
      hintEl.hidden = menuOpen;
    }
    function applyZoom() {
      img.style.transform = zoom === 1 ? "" : "scale(" + zoom + ")";
      syncHint();
    }

    overlay.addEventListener("click", function (e) {
      var row = e.target.closest("[data-a]");
      if (row && row.tagName === "BUTTON") {
        var a = row.dataset.a;
        if (a === "close") { overlay.remove(); return; }
        if (a === "sticker") {
          window.api("POST", "/api/stickers", { url: url })
            .then(function () {
              row.textContent = "已添加到表情包";
              row.disabled = true;
              notice("已添加到表情包");
            })
            .catch(function (err) { notice(err.message); });
          return;
        }
        if (a === "save") {
          // 网页直接开新标签让浏览器接手下载（移动端会进「保存到相册」流程）
          notice("正在保存…");
          var aEl = document.createElement("a");
          aEl.href = url; aEl.download = ""; aEl.target = "_blank";
          document.body.appendChild(aEl); aEl.click(); aEl.remove();
          setTimeout(function () { notice("已保存"); }, 600);
          return;
        }
      }
      if (e.target === img) {
        // 单击：先收起菜单，再关掉看图（安卓单击=关闭）
        if (menuOpen) { menuOpen = false; menuEl.hidden = true; syncHint(); return; }
        overlay.remove();
        return;
      }
      if (e.target === overlay) {
        // 点 scrim：打开菜单
        menuOpen = !menuOpen;
        menuEl.hidden = !menuOpen;
        syncHint();
      }
    });
    img.addEventListener("dblclick", function () {
      zoom = zoom === 1 ? 2 : 1;
      applyZoom();
    });
    img.addEventListener("contextmenu", function (e) {
      e.preventDefault();
      window.open(url, "_blank");
    });
    syncHint();
  };

  // 正文/评论图片点击放大（事件委托）
  document.addEventListener("click", function (e) {
    var img = e.target.closest(".md img, .post-cover-img, .comment .body img");
    if (img && img.src && img.src.indexOf("/uploads/") >= 0) {
      e.preventDefault();
      window.openImageViewer(img.src);
    }
  });

  // ── 提及选择器（@ 唤起 → /api/search/users） ──────────────────
  window.openMentionPicker = function (input, atPos) {
    var overlay = window.shrineSheet(
      '<div class="mention-sheet">' +
      '<div class="form-row"><span class="form-label">提及社员</span>' +
      '<input class="field" data-role="q" placeholder="搜索昵称或编号"></div>' +
      '<div class="mention-list" data-role="list"></div>' +
      "</div>"
    );
    var qEl = overlay.querySelector('[data-role="q"]');
    var list = overlay.querySelector('[data-role="list"]');
    qEl.focus();
    var seq = 0;
    // 防抖与限频重试对齐 MentionPicker.kt:81-103：
    // - 输入停止 220ms 才发请求（delay(220)），逐键直发会被服务端 200ms 限频挡成
    //   「查询太频繁」，让用户误以为查无此人；
    // - 每次按键立刻作废在途请求（安卓 LaunchedEffect(query) 换 key 即取消旧协程），
    //   否则慢响应的旧结果会盖掉新输入；
    // - 服务端仍可能回 429（快速删改/多端同账号），隔 260ms 自动重试一次
    //   （fetchMentionUsers 的 `if (code==429) delay(260); retry` 同款语义）。
    var timer = null;
    function fetch(q, my, retried) {
      window.api("GET", "/api/search/users?q=" + encodeURIComponent(q))
        .then(function (res) {
          if (my !== seq) return;
          var users = res.users || [];
          if (!users.length) { list.innerHTML = '<p class="page-end">未找到相关社员</p>'; return; }
          list.innerHTML = users.map(function (u) {
            return '<button class="user-row" data-no="' + esc(u.no) + '" data-nick="' + esc(u.nickname) + '">' +
              '<span class="name">' + esc(u.nickname) + "</span><span class='mono'>" + esc(u.no) + "</span></button>";
          }).join("");
        })
        .catch(function (e) {
          if (my !== seq) return;
          // window.api 只带中文错误文案、不带 HTTP 状态码；限频文案是唯一的 429 信号
          if (!retried && /频繁/.test(e.message || "")) {
            setTimeout(function () { if (my === seq) fetch(q, my, true); }, 260);
            return;
          }
          list.innerHTML = '<p class="page-end">' + esc(e.message) + "</p>";
        });
    }
    function search() {
      var q = qEl.value.trim().replace(/^@/, "");
      var my = ++seq;
      if (!q) { list.innerHTML = '<p class="page-end">输入昵称或编号</p>'; return; }
      list.innerHTML = '<p class="page-end">正在查找……</p>';
      fetch(q, my, false);
    }
    qEl.addEventListener("input", function () {
      clearTimeout(timer);
      seq++; // 立刻作废在途请求，避免旧词结果盖掉新输入
      var q = qEl.value.trim().replace(/^@/, "");
      if (!q) { list.innerHTML = '<p class="page-end">输入昵称或编号</p>'; return; }
      // 防抖期也摆「在查」反馈（安卓同样在 delay 期间给 Loading 态）
      list.innerHTML = '<p class="page-end">正在查找……</p>';
      timer = setTimeout(search, 220);
    });
    search();
    list.addEventListener("click", function (e) {
      var b = e.target.closest("[data-no]");
      if (!b) return;
      var insert = "@" + b.dataset.no + " ";
      if (atPos !== undefined && atPos >= 0 && input.value[atPos] === "@") {
        input.value = input.value.slice(0, atPos) + insert + input.value.slice(atPos + 1);
      } else {
        input.value += insert;
      }
      overlay.remove();
      input.focus();
    });
  };

  // @ 唤起：输入框里刚敲入一个 @（长度 +1、公共前缀、光标处）
  window.bindMentionTrigger = function (input, onTrigger) {
    var prev = input.value;
    input.addEventListener("input", function () {
      var now = input.value;
      if (now.length === prev.length + 1) {
        var i = 0;
        while (i < prev.length && prev[i] === now[i]) i++;
        if (now[i] === "@") onTrigger(i);
      }
      prev = now;
    });
  };

  // ── 图片上传（/api/upload 多部分表单） ────────────────────────
  // 客户端压缩（ImagePrep.kt:200-255 同规则）：
  //   长边 > 2048 或体积 > 700KB 才重编码成 JPEG 85（PNG 透明保留 PNG）；
  //   GIF 永不重编码（重编码等于杀掉动画）；压完反而更大就用原件。
  var UPLOAD_MAX_DIM = 2048;
  var KEEP_RAW_BYTES = 700 * 1024;

  function needsRecompress(file) {
    if (/gif/i.test(file.type || "")) return Promise.resolve(false);
    if (file.size > KEEP_RAW_BYTES) return Promise.resolve(true);
    return new Promise(function (resolve) {
      var url = URL.createObjectURL(file);
      var img = new Image();
      img.onload = function () {
        URL.revokeObjectURL(url);
        resolve(Math.max(img.naturalWidth, img.naturalHeight) > UPLOAD_MAX_DIM);
      };
      img.onerror = function () { URL.revokeObjectURL(url); resolve(false); };
      img.src = url;
    });
  }

  function recompress(file) {
    return new Promise(function (resolve) {
      var url = URL.createObjectURL(file);
      var img = new Image();
      img.onload = function () {
        URL.revokeObjectURL(url);
        var w = img.naturalWidth, h = img.naturalHeight;
        var longEdge = Math.max(w, h);
        var ratio = longEdge > UPLOAD_MAX_DIM ? UPLOAD_MAX_DIM / longEdge : 1;
        var cw = Math.max(1, Math.round(w * ratio));
        var ch = Math.max(1, Math.round(h * ratio));
        var cv = document.createElement("canvas");
        cv.width = cw; cv.height = ch;
        var cx = cv.getContext("2d");
        // PNG 保留透明；其余先铺白底（安卓 compress JPEG 前也是不透明位图）
        var keepAlpha = /png/i.test(file.type || "");
        if (!keepAlpha) { cx.fillStyle = "#fff"; cx.fillRect(0, 0, cw, ch); }
        cx.drawImage(img, 0, 0, cw, ch);
        cv.toBlob(function (blob) {
          // 压完反而更大就用原件（ImagePrep.recompressWorthIt 同语义）
          if (!blob || blob.size >= file.size) { resolve(file); return; }
          var ext = keepAlpha ? "png" : "jpg";
          var name = (file.name || "image").replace(/\.[^.]+$/, "") + "." + ext;
          resolve(new File([blob], name, { type: keepAlpha ? "image/png" : "image/jpeg" }));
        }, keepAlpha ? "image/png" : "image/jpeg", keepAlpha ? 1 : 0.85);
      };
      img.onerror = function () { URL.revokeObjectURL(url); resolve(file); };
      img.src = url;
    });
  }

  window.uploadImage = function (file, onProgress) {
    // 先按安卓规则决定要不要压，再发起上传
    return needsRecompress(file).then(function (need) {
      return need ? recompress(file) : file;
    }).then(function (out) { return doUpload(out, onProgress); });
  };

  function doUpload(file, onProgress) {
    return new Promise(function (resolve, reject) {
      var fd = new FormData();
      fd.append("file", file, file.name || "image.jpg");
      var xhr = new XMLHttpRequest();
      xhr.open("POST", "/api/upload");
      xhr.upload.addEventListener("progress", function (e) {
        if (e.lengthComputable && onProgress) onProgress(e.loaded / e.total);
      });
      xhr.addEventListener("load", function () {
        try {
          var res = JSON.parse(xhr.responseText);
          if (xhr.status >= 200 && xhr.status < 300) resolve(res.url);
          else reject(new Error(res.error || "上传失败"));
        } catch (e) { reject(new Error("上传失败")); }
      });
      xhr.addEventListener("error", function () { reject(new Error("连接服务器失败，请检查网络或稍后再试")); });
      xhr.send(fd);
    });
  };
})();


// ══ Markdown 工具条（对齐 MarkdownEdit.kt:12-300 的 toggle 语义）══
// 七个动作都是 toggle：光标/选区已在记号内就剥掉，否则包起来。
// 行级动作（标题/列表/引用）按「光标所在行范围」批量处理，全部已是目标样式时取消。
function bindMarkdownToolbar(form, bodyEl) {
  var HeadingRe = /^#{1,6}[ \t]+/;
  var BulletRe = /^[-*+][ \t]+/;
  var OrderedRe = /^\d+\.[ \t]+/;
  var FenceRe = /^```.*$/;

  function lineStart(t, i) { var j = t.lastIndexOf("\n", i - 1); return j < 0 ? 0 : j + 1; }
  function lineEnd(t, i) { var j = t.indexOf("\n", i); return j < 0 ? t.length : j; }
  function lineRange(t, a, b) { return [lineStart(t, a), lineEnd(t, b)]; }

  // 记号边界：前后不是同类字符（避免把 ** 当成 * 的两倍）
  function markerBoundary(t, i, marker, opening) {
    var ch = marker.charAt(0);
    if (opening) return i === 0 || t.charAt(i - 1) !== ch;
    var j = i + marker.length;
    return j >= t.length || t.charAt(j) !== ch;
  }

  function findWrap(t, start, end, prefix, suffix) {
    // 1) 选区本身带记号
    if (end - start >= prefix.length + suffix.length &&
        t.substr(start, prefix.length) === prefix &&
        t.substr(end - suffix.length, suffix.length) === suffix &&
        markerBoundary(t, start, prefix, true) &&
        markerBoundary(t, end - suffix.length, suffix, false)) {
      return [start, end];
    }
    // 2) 记号贴在选区两侧
    if (start >= prefix.length && end + suffix.length <= t.length &&
        t.substr(start - prefix.length, prefix.length) === prefix &&
        t.substr(end, suffix.length) === suffix &&
        markerBoundary(t, start - prefix.length, prefix, true) &&
        markerBoundary(t, end, suffix, false)) {
      return [start - prefix.length, end + suffix.length];
    }
    // 3) 光标落在记号中间
    if (start === end) {
      var from = lineStart(t, start), to = lineEnd(t, end), open = -1, i;
      for (i = from; i < start; i++) {
        if (t.substr(i, prefix.length) === prefix && markerBoundary(t, i, prefix, true)) open = i;
      }
      if (open >= 0) {
        for (i = start; i <= to - suffix.length; i++) {
          if (t.substr(i, suffix.length) === suffix && markerBoundary(t, i, suffix, false)) {
            return [open, i + suffix.length];
          }
        }
      }
    }
    return null;
  }

  function toggleInline(t, start, end, prefix, suffix, trimFirst) {
    var collapsed = start === end;
    var wrap = findWrap(t, start, end, prefix, suffix);
    if (wrap) {
      var open = wrap[0], close = wrap[1];
      var innerStart = open + prefix.length, innerEnd = close - suffix.length;
      var inner = t.slice(innerStart, innerEnd);
      var nt = t.slice(0, open) + inner + t.slice(close);
      var caret = collapsed ? Math.min(Math.max(start - prefix.length, open), open + inner.length) : open;
      var selEnd = collapsed ? caret : open + inner.length;
      return { text: nt, start: caret, end: selEnd };
    }
    if (collapsed) {
      var nt2 = t.slice(0, start) + prefix + suffix + t.slice(start);
      return { text: nt2, start: start + prefix.length, end: start + prefix.length };
    }
    var core = t.slice(start, end), leading = "", trailing = "";
    if (trimFirst) {
      var first = core.search(/\S/), last = core.length - 1;
      while (last >= 0 && /\s/.test(core.charAt(last))) last--;
      if (first >= 0 && last >= first) {
        leading = core.slice(0, first);
        trailing = core.slice(last + 1);
        core = core.slice(first, last + 1);
      }
    }
    var repl = leading + prefix + core + suffix + trailing;
    var nt3 = t.slice(0, start) + repl + t.slice(end);
    var selStart = start + leading.length + prefix.length;
    return { text: nt3, start: selStart, end: selStart + core.length };
  }

  function toggleLines(t, start, end, targetRe, makePrefix, stripRe) {
    var r = lineRange(t, start, end);
    var from = r[0], to = r[1];
    var lines = t.slice(from, to).split("\n");
    var nonEmpty = lines.filter(function (l) { return l.trim() !== ""; });
    var collapsed = start === end;
    if (nonEmpty.length === 0 && !collapsed) return null;
    var prefix = makePrefix();
    if (nonEmpty.length === 0) {
      var nt0 = t.slice(0, from) + prefix + t.slice(to);
      return { text: nt0, start: from + prefix.length, end: from + prefix.length };
    }
    var allTarget = nonEmpty.every(function (l) { return targetRe.test(l); });
    var newLines = lines.map(function (l) {
      if (l.trim() === "") return l;
      var indent = l.match(/^[ \t]*/)[0];
      var bodyL = l.slice(indent.length).replace(stripRe, "");
      return allTarget ? indent + bodyL : indent + prefix + bodyL;
    });
    var nt = t.slice(0, from) + newLines.join("\n") + t.slice(to);
    return { text: nt, start: from, end: from + newLines.join("\n").length };
  }

  function apply(kind) {
    var t = bodyEl.value;
    var start = bodyEl.selectionStart || 0, end = bodyEl.selectionEnd || 0;
    var res = null;
    switch (kind) {
      case "bold":   res = toggleInline(t, start, end, "**", "**", true); break;
      case "italic": res = toggleInline(t, start, end, "*", "*", true); break;
      case "code":   res = toggleInline(t, start, end, "`", "`", false); break;
      case "heading":res = toggleLines(t, start, end, HeadingRe, function () { return "## "; }, HeadingRe); break;
      case "list":   res = toggleLines(t, start, end, BulletRe, function () { return "- "; }, BulletRe); break;
      case "quote":  res = toggleLines(t, start, end, /^>[ \t]?/, function () { return "> "; }, /^>[ \t]?/); break;
      case "link": {
        var sel = t.slice(start, end);
        var linkRe = /^\[([^\]]*)\]\(([^)]*)\)$/;
        if (linkRe.test(sel)) {
          var m = sel.match(linkRe);
          var nt = t.slice(0, start) + m[1] + t.slice(end);
          res = { text: nt, start: start, end: start + m[1].length };
        } else {
          var out = "[" + sel + "](url)";
          var nt2 = t.slice(0, start) + out + t.slice(end);
          var s2 = start + 1;
          res = { text: nt2, start: s2, end: s2 + sel.length };
        }
        break;
      }
    }
    if (!res) return;
    bodyEl.value = res.text;
    bodyEl.focus();
    bodyEl.setSelectionRange(res.start, res.end);
  }

  form.querySelectorAll("[data-md]").forEach(function (btn) {
    btn.addEventListener("click", function () { apply(btn.dataset.md); });
  });
}

// ══ 列表滚动加载（对齐 ChatBoardScreen.kt:291-320 / ShrineBoardScreen 的 PrefetchOnApproach）══
// 安卓滚到接近底部自动拉下一页并把新条目接在列表尾；网页同样处理：
// 用 IntersectionObserver 监听列表末尾的收尾行，命中就拉 /b/:board/more 追加。
// 失败时把收尾行换成可点的「加载更多失败，点击重试」。
function bindInfiniteList() {
  var holder = document.querySelector("[data-infinite]");
  if (!holder) return;
  var list = document.querySelector(".board-list, .ema-list");
  var endEl = document.querySelector('[data-role="list-end"]');
  if (!list || !endEl) return;

  var board = holder.dataset.board || "main";
  var sort = holder.dataset.sort || "new";
  var page = parseInt(holder.dataset.page, 10) || 1;
  var total = parseInt(holder.dataset.total, 10) || 0;
  var loading = false;
  var done = endEl.textContent.indexOf("已经到底了") >= 0;

  function esc(s) { return window.escHTML(s == null ? "" : s); }

  function cardHTML(it) {
    var badges = "";
    if (it.pinned) badges += '<span class="meta-badge tint-primary">置顶</span>';
    if (it.essence) badges += '<span class="meta-badge tint-gold">精华</span>';
    var cover = it.cover
      ? '<span class="pc-cover"><img src="' + esc(it.cover) + '" alt="" loading="lazy" referrerpolicy="no-referrer"></span>'
      : '<span class="pc-cover"></span>';
    return '<a class="post-card" href="/post/' + esc(it.no) + '">' +
      cover +
      '<span class="pc-body">' +
      (badges ? '<span class="pc-badges">' + badges + '</span>' : "") +
      '<span class="pc-spring1"></span>' +
      '<span class="pc-title-clamp"><span class="pc-title">' + esc(it.title) + '</span></span>' +
      (it.excerpt ? '<span class="pc-excerpt one">' + esc(it.excerpt) + '</span>' : "") +
      '<span class="pc-spring4"></span>' +
      '<span class="pc-author"><span class="avatar av22" data-role="' + esc(it.role) + '">' + esc(it.author.charAt(0)) + '</span>' +
      '<span class="name">' + esc(it.author) + '</span></span>' +
      '<span class="pc-counts"><span class="icount">顶 ' + it.up + '</span><span class="icount">评论 ' + it.comment + '</span>' +
      '<span class="pc-time">' + esc(it.time) + '</span></span>' +
      "</span></a>";
  }

  var kind = holder.dataset.kind || "post";
  var qs = holder.dataset.qs || "";

  function emaCardHTML(it) {
    var tags = (it.tags || []).map(function (t) {
      return '<span class="tagpill tt-' + esc(t) + '"><i></i>' + esc(t) + "</span>";
    }).join("") || '<span class="tagpill tt-enhance ph" aria-hidden="true"><i></i>enhance</span>';
    return '<a class="ema-card" href="/ema/' + it.id + '">' +
      '<span class="avatar ring" data-role="' + esc(it.role) + '">' + esc(it.author.charAt(0)) + "</span>" +
      '<span class="ec-main">' +
      '<span class="ec-head"><b class="no">#' + it.id + "</b> " +
      '<span class="ec-title">' + esc(it.title) + "</span> " +
      '<span class="stpill t-' + esc(it.state) + '">' + esc(it.state_label) + "</span></span>" +
      '<span class="ec-tags">' + tags + "</span>" +
      '<span class="ec-sum">' + esc(it.excerpt || "") + "</span>" +
      '<span class="ec-foot"><span class="ec-name">' + esc(it.author) + "</span>" +
      '<span class="role-tag" data-role="' + esc(it.role) + '">' + esc(it.role) + "</span>" +
      '<span class="ec-time">' + esc(it.time) + "</span>" +
      '<span class="ec-gap"></span><span class="icount">评论 ' + it.replies + "</span></span>" +
      "</span></a>";
  }

  function loadMore() {
    if (loading || done) return;
    loading = true;
    endEl.textContent = "少女祈祷中……";
    if (kind === "ema") {
      window.api("GET", "/shrine/more?page=" + (page + 1) + (qs ? "&" + qs : ""))
        .then(function (r) {
          var box = list || document.querySelector(".ema-list");
          (r.items || []).forEach(function (it) {
            box.insertAdjacentHTML("beforeend", emaCardHTML(it));
          });
          page += 1;
          total = r.total || total;
          done = !r.has_more;
          endEl.textContent = done ? "已经到底了 · 共 " + total + " 条" : "上滑加载更多";
          loading = false;
        })
        .catch(function (e) {
          loading = false;
          endEl.textContent = "加载更多失败，点击重试";
          endEl.setAttribute("data-retry", "1");
          if (e && e.message) window.toast(e.message);
        });
      return;
    }
    window.api("GET", "/b/" + board + "/more?sort=" + sort + "&page=" + (page + 1))
      .then(function (r) {
        (r.items || []).forEach(function (it) {
          list.insertAdjacentHTML("beforeend", cardHTML(it));
        });
        page += 1;
        total = r.total || total;
        if (!r.has_more) {
          done = true;
          endEl.textContent = "已经到底了";
        } else {
          endEl.textContent = "上滑加载更多";
        }
        loading = false;
      })
      .catch(function (e) {
        loading = false;
        endEl.textContent = "加载更多失败，点击重试";
        endEl.setAttribute("data-retry", "1");
        if (e && e.message) window.toast(e.message);
      });
  }

  endEl.addEventListener("click", function () {
    if (endEl.getAttribute("data-retry") === "1") {
      endEl.removeAttribute("data-retry");
      loadMore();
    }
  });

  if ("IntersectionObserver" in window) {
    new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) loadMore();
      });
    }, { rootMargin: "480px 0px" }).observe(endEl);
  }
}

// ══ 通用文案（逐字取自安卓源码，避免各处自行措辞）══
// 加载更多失败：ChatBoardScreen / ShrineBoardScreen / InboxScreen / ReportsScreen 共用
window.WEB_COPY = {
  loadMoreFailed: "加载更多失败，点击重试",
  praying: "少女祈祷中",
  loadingMore: "少女祈祷中……",   /* ShrineRefreshingHint（States.kt:295-315） */
  listEnd: "到底了",
  coverPreview: "封面预览",
  pendingImages: "待发配图",
  chooseCover: "选择封面图",
  editThisImage: "编辑这张图",
  removeThisImage: "移除这张图",
  mdHint: "正文支持 Markdown，行文简洁即可。",
  postCover: "帖子封面",
  commentImage: "评论附图",
  pendingCommentImage: "待发送的附图",
  changeAvatar: "更换头像",
  activeAvatarOff: "关闭后所有地方将显示固定头像",
  codeSentNewEmail: "验证码已寄到新邮箱（开发模式见服务器日志）",
  emptyCollection: "这个收藏夹还没有内容",
  playFooter: "您的每一秒驻足都会为神社增添香火",
  drawing: "摇 签 中",
  gotIt: "知道了",
  currentRole: "现任",
  pwdResetDone: "密码已重置，用新密码登录吧",
  cropHint: "拖动调整位置，双指缩放",
  cropBadFormat: "识别不出这张图片的格式，请换一张试试",
};

// ══ 图片裁剪器（ImageEditorOverlay.kt:113-260 + ImageTransform.kt:18-62）══
// 安卓的发图流程是「选图 → 裁剪器 → 确定后才上传」，网页此前选完直接上传，
// 用户拿不到取景的机会。这里补齐同一套：
//   · 取景框按比例（原图/1:1/4:3/16:9，封面锁 16:9）等比嵌进中间，
//     框内是成图、框外压暗，一圈 1.5dp 白线标边界；
//   · 单指拖动平移、双指缩放（1.0 ~ 3.0 倍），位移夹在「图始终盖住框」内；
//   · 「旋转」把位图顺时针转 90°，取景复位；
//   · 「确定」按取景框把可见区裁出来，长边封顶 1600，PNG 继续 PNG（保透明底），
//     其余转 JPEG 88（先铺白底，否则 alpha 变黑）。
// 与安卓唯一的差别是产物不落临时文件，直接回一个 Blob 交给上传。
(function () {
  var CROP_MAX_ZOOM = 3;       // ImageTransform.kt:14 CROP_MAX_ZOOM
  var EDIT_OUT_MAX = 1600;     // ImageEditorOverlay.kt:76
  var ASPECTS = [["原图", null], ["1:1", 1], ["4:3", 4 / 3], ["16:9", 16 / 9]];

  function coverFit(iw, ih, vw, vh) {
    return Math.max(vw / iw, vh / ih);      // ImageTransform.kt:61-62
  }
  function clampOffset(x, y, iw, ih, scale, vw, vh) {
    var maxX = Math.max(0, (iw * scale - vw) / 2);
    var maxY = Math.max(0, (ih * scale - vh) / 2);
    return { x: Math.min(maxX, Math.max(-maxX, x)), y: Math.min(maxY, Math.max(-maxY, y)) };
  }

  // rotate90 顺时针转 90°（RotateRight 图标的方向）
  function rotate90(src) {
    var cv = document.createElement("canvas");
    cv.width = src.height; cv.height = src.width;
    var cx = cv.getContext("2d");
    cx.translate(cv.width / 2, cv.height / 2);
    cx.rotate(Math.PI / 2);
    cx.drawImage(src, -src.width / 2, -src.height / 2);
    return cv;
  }

  /**
   * 打开裁剪器。opts:
   *   aspect   锁死比例（封面传 16/9），不给换
   *   onConfirm(blob, ext) 拿到裁好的图
   *   onCancel()
   */
  window.openImageEditor = function (file, opts) {
    opts = opts || {};
    var locked = typeof opts.aspect === "number" ? opts.aspect : null;
    var aspects = locked != null ? [["", locked]] : ASPECTS;
    // 头像走圆形取景（AvatarCropOverlay.kt:120-190）：方形框 + 圆外压暗、无旋转无比例。
    // 框取 min(屏宽-64, 320)（:142），产物 640 见方 JPEG（:65-70）。
    var circle = !!opts.circle;
    var ROT_SVG = '<svg class="ic" viewBox="0 0 24 24"><path d="M15.55 5.55L11 1v3.07C7.06 4.56 4 7.92 4 12s3.05 7.44 7 7.93v-2.02c-2.84-.48-5-2.94-5-5.91s2.16-5.43 5-5.91V10l4.55-4.45zM19.93 11a7.9 7.9 0 00-1.62-3.89l-1.42 1.42c.54.75.88 1.6 1.02 2.47h2.02zM13 17.91v2.02c.9-.12 1.75-.41 2.53-.85l-1.42-1.42a-.99.99 0 00-1.11.25zm3.89-1.62 1.42 1.42a7.85 7.85 0 001.62-3.89h-2.02c-.14.87-.48 1.72-1.02 2.47z"/></svg>';

    var overlay = document.createElement("div");
    overlay.className = "img-editor";
    overlay.innerHTML =
      '<div class="ie-bar">' +
      '<button class="top-ic back" data-a="cancel" aria-label="取消">' +
      '<svg class="ic" viewBox="0 0 24 24"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>' +
      "</button>" +
      "<h1>" + (circle ? "调整头像" : "编辑图片") + "</h1></div>" +
      '<div class="ie-stage"><div class="ie-frame' + (circle ? " round" : "") + '" data-a="frame"><canvas data-a="canvas"></canvas></div>' +
      '<p class="ie-msg" data-a="msg" hidden></p></div>' +
      '<p class="ie-hint">拖动调整位置，双指缩放</p>' +
      '<div class="ie-tools">' +
      (circle ? "" :
      '<button class="ie-rot" data-a="rotate">' + ROT_SVG +
      "<span>旋转</span></button>") +
      (circle ? "" :
      '<div class="ie-aspects">' +
      aspects.map(function (a, i) {
        return a[0] ? '<button class="pill' + (i === 0 ? " on" : "") + '" data-a="aspect" data-i="' + i + '">' + a[0] + "</button>" : "";
      }).join("") +
      "</div>") + "</div>" +
      '<div class="ie-actions"><button class="btn" data-a="ok">确定</button></div>';
    document.body.appendChild(overlay);

    var frame = overlay.querySelector('[data-a="frame"]');
    var canvas = overlay.querySelector('[data-a="canvas"]');
    var msgEl = overlay.querySelector('[data-a="msg"]');
    var okBtn = overlay.querySelector('[data-a="ok"]');
    var rotBtn = overlay.querySelector('[data-a="rotate"]');
    var aspectBtns = Array.prototype.slice.call(overlay.querySelectorAll('[data-a="aspect"]'));

    var bmp = null, aspectIdx = 0, busy = false;
    // 位图以 canvas 承载：旋转后直接换掉内容，尺寸随之变化
    var state = { zoom: 1, x: 0, y: 0 };

    function resetView() { state.zoom = 1; state.x = 0; state.y = 0; }

    function frameBox() {
      var stage = overlay.querySelector(".ie-stage");
      if (circle) {
        // min(屏宽-64, 320)（AvatarCropOverlay.kt:142）：方阵，居中
        var side = Math.min(overlay.clientWidth - 64, 320);
        return { w: Math.round(side), h: Math.round(side) };
      }
      var ratio = aspects[aspectIdx][1] == null
        ? (bmp ? bmp.width / bmp.height : 1)
        : aspects[aspectIdx][1];
      var availW = stage.clientWidth - 48;              // 左右 24dp（ImageEditorOverlay.kt:259）
      var availH = stage.clientHeight;
      var w = Math.min(availW, availH * ratio);
      return { w: Math.round(w), h: Math.round(w / ratio) };
    }

    function draw() {
      if (!bmp) return;
      var fb = frameBox();
      frame.style.width = fb.w + "px";
      frame.style.height = fb.h + "px";
      // 画布的 CSS 尺寸 = 框，内部分辨率跟像素密度走，避免发虚
      var dpr = Math.min(3, window.devicePixelRatio || 1);
      canvas.style.width = fb.w + "px";
      canvas.style.height = fb.h + "px";
      canvas.width = Math.round(fb.w * dpr);
      canvas.height = Math.round(fb.h * dpr);
      var cx = canvas.getContext("2d");
      // 画布按 dpr 放大，绘制仍用 CSS 像素：先整体放大坐标系，否则图只画出一半大
      cx.setTransform(dpr, 0, 0, dpr, 0, 0);
      cx.clearRect(0, 0, fb.w, fb.h);
      var fit = coverFit(bmp.width, bmp.height, fb.w, fb.h);
      var scale = fit * state.zoom;
      var dw = bmp.width * scale, dh = bmp.height * scale;
      var left = (fb.w - dw) / 2 + state.x;
      var top = (fb.h - dh) / 2 + state.y;
      cx.imageSmoothingQuality = "high";
      cx.drawImage(bmp, left, top, dw, dh);
    }

    function applyDelta(dx, dy, zoomChange) {
      if (!bmp) return;
      var fb = frameBox();
      state.zoom = Math.min(CROP_MAX_ZOOM, Math.max(1, state.zoom * (zoomChange || 1)));
      var fit = coverFit(bmp.width, bmp.height, fb.w, fb.h);
      var o = clampOffset(state.x + dx, state.y + dy, bmp.width, bmp.height, fit * state.zoom, fb.w, fb.h);
      state.x = o.x; state.y = o.y;
      draw();
    }

    // 单指拖动 / 双指缩放：对齐 detectTransformGestures（pan + zoomChange）
    var pointers = {}, lastDist = 0, dragging = false, lastX = 0, lastY = 0;
    frame.addEventListener("pointerdown", function (e) {
      if (!bmp) return;
      frame.setPointerCapture(e.pointerId);
      pointers[e.pointerId] = { x: e.clientX, y: e.clientY };
      var ids = Object.keys(pointers);
      if (ids.length === 1) { dragging = true; lastX = e.clientX; lastY = e.clientY; }
      else { dragging = false; lastDist = 0; }
    });
    frame.addEventListener("pointermove", function (e) {
      if (!bmp || !pointers[e.pointerId]) return;
      pointers[e.pointerId] = { x: e.clientX, y: e.clientY };
      var ids = Object.keys(pointers);
      if (ids.length === 1 && dragging) {
        applyDelta(e.clientX - lastX, e.clientY - lastY, 1);
        lastX = e.clientX; lastY = e.clientY;
      } else if (ids.length >= 2) {
        var a = pointers[ids[0]], b = pointers[ids[1]];
        var d = Math.hypot(a.x - b.x, a.y - b.y);
        if (lastDist > 0) applyDelta(0, 0, d / lastDist);
        lastDist = d;
      }
    });
    function pointerEnd(e) {
      delete pointers[e.pointerId];
      if (Object.keys(pointers).length < 2) lastDist = 0;
      if (Object.keys(pointers).length === 0) dragging = false;
    }
    frame.addEventListener("pointerup", pointerEnd);
    frame.addEventListener("pointercancel", pointerEnd);

    // 滚轮也能缩放：桌面浏览器没有双指
    frame.addEventListener("wheel", function (e) {
      if (!bmp) return;
      e.preventDefault();
      applyDelta(0, 0, e.deltaY < 0 ? 1.06 : 1 / 1.06);
    }, { passive: false });

    if (rotBtn) rotBtn.addEventListener("click", function () {
      if (!bmp || busy) return;
      bmp = rotate90(bmp);
      resetView();
      draw();
    });
    aspectBtns.forEach(function (b) {
      b.addEventListener("click", function () {
        aspectIdx = parseInt(b.dataset.i, 10);
        aspectBtns.forEach(function (x) { x.classList.toggle("on", x === b); });
        resetView();
        draw();
      });
    });

    function close() {
      overlay.remove();
    }
    overlay.querySelector('[data-a="cancel"]').addEventListener("click", function () {
      close();
      if (opts.onCancel) opts.onCancel();
    });

    okBtn.addEventListener("click", function () {
      if (!bmp || busy) return;
      var fb = frameBox();
      var fit = coverFit(bmp.width, bmp.height, fb.w, fb.h);
      var scale = fit * state.zoom;
      var srcW = fb.w / scale, srcH = fb.h / scale;
      if (circle) {
        // 圆的外接正方形：四个角显示时同样被裁掉，裁方形即所见即所得（AvatarCropOverlay.kt:214-247）
        var side = Math.max(1, Math.round(srcW));
        var lx = Math.min(Math.max(0, bmp.width / 2 - (fb.w / 2 + state.x) / scale), Math.max(0, bmp.width - side));
        var ty = Math.min(Math.max(0, bmp.height / 2 - (fb.h / 2 + state.y) / scale), Math.max(0, bmp.height - side));
        var sq = document.createElement("canvas");
        sq.width = 640; sq.height = 640;      // CROP_OUT（AvatarCropOverlay.kt:65）
        var sqx = sq.getContext("2d");
        sqx.fillStyle = "#fff"; sqx.fillRect(0, 0, 640, 640);
        sqx.imageSmoothingQuality = "high";
        sqx.drawImage(bmp, Math.round(lx), Math.round(ty), side, side, 0, 0, 640, 640);
        busy = true;
        okBtn.disabled = true;
        sq.toBlob(function (blob) {
          close();
          if (opts.onConfirm) opts.onConfirm(blob, "jpg");
        }, "image/jpeg", 0.88);
        return;
      }
      // 取景框左上角对应的原图坐标；夹回图内（ImageEditorOverlay.kt:330-345）
      var left = Math.min(Math.max(0, bmp.width / 2 - (fb.w / 2 + state.x) / scale), Math.max(0, bmp.width - srcW));
      var top = Math.min(Math.max(0, bmp.height / 2 - (fb.h / 2 + state.y) / scale), Math.max(0, bmp.height - srcH));
      var sw = Math.max(1, Math.round(srcW)), sh = Math.max(1, Math.round(srcH));
      var long = Math.max(sw, sh);
      var outScale = long > EDIT_OUT_MAX ? EDIT_OUT_MAX / long : 1;
      var outW = Math.max(1, Math.round(sw * outScale));
      var outH = Math.max(1, Math.round(sh * outScale));
      var isPNG = /\.png$/i.test(file.name || "") || file.type === "image/png";
      var out = document.createElement("canvas");
      out.width = outW; out.height = outH;
      var cx = out.getContext("2d");
      // 带透明通道的图压成 JPEG 会丢 alpha，先铺白底
      if (!isPNG) { cx.fillStyle = "#fff"; cx.fillRect(0, 0, outW, outH); }
      cx.imageSmoothingQuality = "high";
      cx.drawImage(bmp, Math.round(left), Math.round(top), sw, sh, 0, 0, outW, outH);
      var ext = isPNG ? "png" : "jpg";
      busy = true;
      okBtn.disabled = true;
      out.toBlob(function (blob) {
        close();
        if (opts.onConfirm) opts.onConfirm(blob, ext);
      }, isPNG ? "image/png" : "image/jpeg", 0.88);
    });

    // 解码：优先 image.decode()（自带 EXIF 方向），失败落提示
    var url = URL.createObjectURL(file);
    var probe = new Image();
    probe.onload = function () {
      var cv = document.createElement("canvas");
      cv.width = probe.naturalWidth;
      cv.height = probe.naturalHeight;
      cv.getContext("2d").drawImage(probe, 0, 0);
      bmp = cv;
      URL.revokeObjectURL(url);
      okBtn.disabled = false;
      draw();
    };
    probe.onerror = function () {
      URL.revokeObjectURL(url);
      frame.hidden = true;
      msgEl.hidden = false;
      msgEl.textContent = window.WEB_COPY.cropBadFormat;
    };
    okBtn.disabled = true;
    probe.src = url;
  };
})();

// ══ 分享卡片（ShareCard.kt:113-420）：帖子与绘马详情页共用同一套渲染 ══
(function () {
  // 1080 宽、左右 72 边距；顶部极淡朱息 420 高；左上 64×64 朱印「有」+
  // 「有栖神社」/「帖子 · 板块」；正文区标题 46sp 粗、朱色细线、正文 32sp；
  // 尾部作者 26sp + 时间 23sp，右下 84×84 朱印「有栖」。
  function buildShareCard(data) {
    var W = 1080, PAD = 72, CW = W - PAD * 2, MAXH = 2400, MINH = 960;
    var cv = document.createElement("canvas");
    cv.width = W; cv.height = MAXH;
    var c = cv.getContext("2d");
    var paper = "#f9f7f4", ink = "#1a1512", bodyInk = "#2e2723", muted = "#6e655d";
    var verm = "#b02a21";
    var serif = '"Arisu Serif","Noto Serif SC","Source Han Serif SC","Songti SC",serif';

    function wrap(text, size, weight, maxW, maxLines) {
      c.font = weight + " " + size + "px " + serif;
      var lines = [], cur = "";
      for (var i = 0; i < text.length; i++) {
        var ch = text[i];
        if (ch === "\n") { lines.push(cur); cur = ""; if (lines.length >= maxLines) break; continue; }
        var t = cur + ch;
        if (c.measureText(t).width > maxW && cur) {
          lines.push(cur); cur = ch;
          if (lines.length >= maxLines) break;
        } else cur = t;
      }
      if (lines.length < maxLines && cur) lines.push(cur);
      return lines;
    }

    var titleLines = wrap(data.title || "无题", 46, "700", CW, 3);
    var bodyLines = 10, lines = wrap(data.body, 32, "400", CW, bodyLines);
    var LH_T = 64, LH_B = 50;
    function heightOf(tl, bl) {
      return 232 + tl.length * LH_T + 34 + 1 + 34 + bl.length * LH_B + 56 + 96 + PAD;
    }
    var h = heightOf(titleLines, lines);
    while (bodyLines > 2 && h > MAXH) {
      bodyLines--; lines = wrap(data.body, 32, "400", CW, bodyLines);
      h = heightOf(titleLines, lines);
    }
    h = Math.max(MINH, Math.min(MAXH, h));
    cv.height = h;

    c.fillStyle = paper; c.fillRect(0, 0, W, h);
    var g = c.createLinearGradient(0, 0, 0, 420);
    g.addColorStop(0, "rgba(176,42,33,0.118)"); g.addColorStop(1, "rgba(176,42,33,0)");
    c.fillStyle = g; c.fillRect(0, 0, W, 420);

    // 头部
    c.fillStyle = verm;
    roundRect(c, PAD, 74, 64, 64, 12); c.fill();
    c.fillStyle = "#fff"; c.font = "700 34px " + serif; c.textAlign = "center";
    c.fillText("有", PAD + 32, 74 + 32 + 12);
    c.textAlign = "left";
    c.fillStyle = ink; c.font = "600 30px " + serif;
    c.fillText("有栖神社", PAD + 84, 112);
    c.fillStyle = muted; c.font = "400 23px " + serif;
    c.fillText(data.kind + " · " + data.meta, PAD + 84, 144);
    c.textAlign = "right";
    c.fillText(data.id, W - PAD, 112);
    c.textAlign = "left";

    // 正文
    var y = 232;
    c.fillStyle = ink; c.font = "700 46px " + serif;
    titleLines.forEach(function (ln) { c.fillText(ln, PAD, y + 46); y += LH_T; });
    y += 34 - LH_T + LH_T;
    c.fillStyle = "rgba(176,42,33,0.18)";
    c.fillRect(PAD, y, CW, 1.5);
    y += 34;
    c.fillStyle = bodyInk; c.font = "400 32px " + serif;
    lines.forEach(function (ln) { c.fillText(ln, PAD, y + 32); y += LH_B; });

    // 尾部
    var fy = h - PAD - 40;
    c.fillStyle = bodyInk; c.font = "600 26px " + serif;
    c.fillText(data.author, PAD, fy);
    c.fillStyle = muted; c.font = "400 23px " + serif;
    c.fillText(data.time, PAD, fy + 34);
    c.fillStyle = verm;
    roundRect(c, W - PAD - 84, h - PAD - 84, 84, 84, 14); c.fill();
    c.fillStyle = "#fff"; c.font = "600 26px " + serif; c.textAlign = "center";
    c.fillText("有栖", W - PAD - 42, h - PAD - 42 + 9);
    return cv;
  }
  function roundRect(c, x, y, w, h, r) {
    c.beginPath();
    c.moveTo(x + r, y);
    c.arcTo(x + w, y, x + w, y + h, r);
    c.arcTo(x + w, y + h, x, y + h, r);
    c.arcTo(x, y + h, x, y, r);
    c.arcTo(x, y, x + w, y, r);
    c.closePath();
  }
  window.buildShareCard = buildShareCard;
  window.__shareRoundRect = roundRect;
})();

// ══ 帖子详情页（data-page="post"）：互动条/菜单/奉纳/收藏/评论 ══
(function () {
  if (document.body.dataset.page !== "post") return;
  var detail = document.querySelector(".post-detail");
  if (!detail) return;
  var postID = detail.dataset.postId;
  var mine = detail.dataset.mine === "1";
  var canDelete = detail.dataset.canDelete === "1";
  var favorited = detail.dataset.favorited === "1";
  var balance = parseInt(detail.dataset.balance || "0", 10);
  // 奉纳累计额：模板里显示的是 cn() 缩写（1.2万），原值挂在 data-total
  var offerTotal = parseInt(document.querySelector('[data-role="offer"]')?.dataset.total || "0", 10);

  // ── 顶 / 追踪 / 收藏（乐观更新 + 失败回滚） ──────────────────
  var upBtn = document.querySelector('[data-role="up"]');
  upBtn.addEventListener("click", function () {
    window.api("POST", "/api/posts/" + postID + "/up").then(function (r) {
      upBtn.querySelector('[data-c="up"]').textContent = r.up_count;
      upBtn.classList.toggle("on", r.upped);
      var c = upBtn.querySelector('[data-c="up"]');
      c.classList.remove("pop"); void c.offsetWidth; c.classList.add("pop");
    }).catch(function (e) { toast(e.message); });
  });
  var trackBtn = document.querySelector('[data-role="track"]');
  trackBtn.addEventListener("click", function () {
    window.api("POST", "/api/posts/" + postID + "/track").then(function (r) {
      trackBtn.classList.toggle("on", r.tracked);
      toast(r.tracked ? "已追踪，有新评论会通知您" : "已取消追踪");
    }).catch(function (e) { toast(e.message); });
  });
  document.querySelector('[data-role="jump"]').addEventListener("click", function () {
    var input = document.querySelector('[data-role="input"]');
    if (input) { input.scrollIntoView({ behavior: "smooth" }); input.focus(); }
  });

  // ── 收藏夹 sheet（POST /favorite，collection_id=0 即移出） ────
  var favBtn = document.querySelector('[data-role="fav"]');
  favBtn.addEventListener("click", function () {
    window.api("GET", "/api/me/collections").then(function (res) {
      var cols = res.items || [];
      var listHTML = cols.map(function (col) {
        var sub = col.count + " 个内容" + (col.isDefault ? " · 默认收藏夹" : "");
        return '<button class="col-row" data-cid="' + col.id + '">' +
          '<span class="col-av">' + window.escHTML((col.name || "收")[0]) + "</span>" +
          '<span class="col-main"><b>' + window.escHTML(col.name) + "</b><span>" + sub + "</span></span>" +
          '<span class="col-tick" aria-hidden="true"></span></button>';
      }).join("");
      // 面板结构照 PostDetailScreen.kt 的「收藏到」：标题 + 一行说明 + 收藏夹列表
      // （32dp 头像 + 名称 +「N 个内容 · 默认收藏夹」+ 右侧选中勾）+ 新建 +
      //「确认」「取消」两个出口。点条目只改选中，按确认才提交；
      // 再点已选中项 = 取消选中（安卓同语义）。
      var overlay = window.shrineSheet(
        '<div class="sheet-head"><span class="sheet-grip"></span><h3>收藏到</h3>' +
        '<p class="sheet-sub">同一份内容只保存在一个收藏夹</p></div>' +
        '<div class="col-list">' + listHTML + "</div>" +
        '<div class="form-row" style="margin-top:14px"><div class="code-row">' +
        '<div class="well grow"><input class="field" data-role="newname" maxlength="20" placeholder="新建收藏夹（最多 20 字）"></div>' +
        '<button class="btn-ghost" data-role="create">新建</button></div>' +
        '<p class="form-error" data-role="cerr" style="display:none"></p></div>' +
        '<div class="col-actions">' +
        '<button class="btn" data-role="confirm">确认</button>' +
        (favorited ? '<button class="btn-ghost" data-role="remove">移出收藏</button>' : '<button class="btn-ghost" data-role="cancel">取消</button>') +
        '</div>'
      );
      var picked = null;   // 当前选中收藏夹 id（null = 不选）
      function syncPicked() {
        overlay.querySelectorAll("[data-cid]").forEach(function (b) {
          b.classList.toggle("on", picked !== null && +b.dataset.cid === picked);
        });
      }
      overlay.querySelector(".col-list").addEventListener("click", function (e) {
        var b = e.target.closest("[data-cid]");
        if (!b) return;
        var id = +b.dataset.cid;
        picked = (picked === id) ? null : id;
        syncPicked();
      });
      var cancelBtn = overlay.querySelector('[data-role="cancel"]');
      if (cancelBtn) cancelBtn.addEventListener("click", function () { window.closeSheets(); });
      overlay.querySelector('[data-role="confirm"]').addEventListener("click", function () {
        if (picked === null) { window.closeSheets(); return; }
        var nm = overlay.querySelector('[data-cid="' + picked + '"] .name');
        favorite(picked, nm ? nm.textContent.trim() : "");
      });
      var removeBtn = overlay.querySelector('[data-role="remove"]');
      if (removeBtn) removeBtn.addEventListener("click", function () { favorite(0, ""); });
      var createBtn = overlay.querySelector('[data-role="create"]');
      createBtn.addEventListener("click", function () {
        var name = overlay.querySelector('[data-role="newname"]').value.trim();
        if (!name) return;
        window.api("POST", "/api/me/collections", { name: name })
          .then(function () {
            overlay.querySelector('[data-role="cerr"]').style.display = "none";
            toast("收藏夹已创建");
            window.closeSheets();
            favBtn.click();
          })
          .catch(function (e) {
            var er = overlay.querySelector('[data-role="cerr"]');
            er.textContent = e.message; er.style.display = "block";
          });
      });
      function favorite(cid, name) {
        window.api("POST", "/api/posts/" + postID + "/favorite", { collection_id: cid })
          .then(function (r) {
            favorited = r.favorited;
            favBtn.classList.toggle("on", favorited);
            var c = favBtn.querySelector('[data-c="fav"]');
            if (r.favorited) { c.textContent = parseInt(c.textContent || "0", 10) + 1; }
            else { c.textContent = Math.max(0, parseInt(c.textContent || "0", 10) - 1); }
            window.closeSheets();
            toast(r.favorited ? "已收藏到「" + name + "」" : "已移出收藏");
          })
          .catch(function (e) { toast(e.message); });
      }
    }).catch(function (e) { toast(e.message); });
  });

  // ── 奉纳弹层（OfferSheet 口径） ───────────────────────────────
  // 鸟居图形（弹层徽记用；与侧栏/底栏同一枚形状）
  var TORII_SVG = '<svg class="ic" viewBox="0 0 24 24"><path d="M0.8 3.7 Q12 1.3 23.2 3.7 L23.2 5.9 Q12 3.5 0.8 5.9 Z M2.7 6.1 H21.3 V7.9 H2.7 Z M10.7 7.9 H13.3 V10.7 H10.7 Z M3.5 10.7 H20.5 V12.5 H3.5 Z M5.7 7.9 H8 V22.2 H5.7 Z M16 7.9 H18.3 V22.2 H16 Z"/></svg>';

  // ── 奉纳弹层（SaisenBox.kt:128-390）──────────────────────────
  // 结构：朱鸟居徽记（118 高）+「已奉纳 N 円」金色累计 + 阴阳玉分隔桥 +
  //      余额说明 + 金额细线框 + 5/10/50 圆快捷钮 + 就地错误槽 + 奉纳/取消
  // 成立后：一枚「奉」圆印（双环 + 衬线印文）以弹跳落定在鸟居正中，
  //        累计额弹一下，阴阳玉停转；整段不可关闭，约 750ms 后自动收起。
  document.querySelector('[data-role="offer"]').addEventListener("click", function () {
    var overlay = window.shrineSheet(
      '<div class="offer-sheet">' +
      '<div class="offer-crest">' +
      '<span class="offer-torii">' + TORII_SVG + '</span>' +
      '<span class="offer-seal" data-role="offer-seal" hidden><b>奉</b><i></i></span>' +
      '</div>' +
      '<p class="offer-total" data-role="total">已奉纳 ' + offerTotal + ' 円</p>' +
      '<div class="offer-bridge"><span></span><i class="yy-orb" data-role="orb"></i><span></span></div>' +
      '<p class="offer-balance">我的赛钱 ' + (balance / 10) + ' 円 · 作者收其一成</p>' +
      '<div class="offer-input-row">' +
      '<input class="offer-amount" data-role="amount" type="text" inputmode="numeric" placeholder="金额（円）">' +
      '<button class="offer-chip" data-role="chip" data-n="5">5</button>' +
      '<button class="offer-chip" data-role="chip" data-n="10">10</button>' +
      '<button class="offer-chip" data-role="chip" data-n="50">50</button>' +
      '</div>' +
      '<p class="offer-err" data-role="err"></p>' +
      '<div class="offer-actions">' +
      '<button class="offer-go" data-role="go">奉纳</button>' +
      '<button class="offer-cancel" data-role="cancel">取消</button>' +
      '</div>' +
      "</div>"
    );
    var $ = function (r) { return overlay.querySelector('[data-role="' + r + '"]'); };
    var amountEl = $("amount"), goBtn = $("go"), errEl = $("err");
    var totalEl = $("total"), sealEl = $("offer-seal"), orbEl = $("orb");
    var locked = false;

    amountEl.addEventListener("input", function () {
      amountEl.value = amountEl.value.replace(/[^0-9]/g, "").slice(0, 6);
      errEl.textContent = "";
    });
    overlay.querySelectorAll('[data-role="chip"]').forEach(function (b) {
      var n = +b.dataset.n;
      if (balance < n * 10) b.disabled = true;
      b.addEventListener("click", function () {
        if (locked || b.disabled) return;
        amountEl.value = String(n);
        errEl.textContent = "";
      });
    });
    $("cancel").addEventListener("click", function () {
      if (!locked) overlay.remove();
    });

    goBtn.addEventListener("click", function () {
      if (locked) return;
      var amount = parseInt(amountEl.value, 10) || 0;
      if (amount < 1) return;
      // 余额不足也要能点：先就地提示，真被服务端拒绝还有一层兜底
      if (amount * 10 > balance) {
        errEl.textContent = "赛钱不足，请明日再来";
        return;
      }
      locked = true;
      goBtn.textContent = "奉纳中";
      goBtn.classList.add("busy");
      window.api("POST", "/api/posts/" + postID + "/offer", { amount: amount })
        .then(function (r) {
          balance = r.balance_tenths;
          offerTotal = r.offer_total;
          document.querySelector('[data-role="offer"] b').textContent =
            (r.offer_total >= 10000 ? (r.offer_total / 10000).toFixed(1).replace(/\.0$/, "") + "万" : r.offer_total);
          // 盖章：大尺入场 → 弹跳落定 -7°，印环扩散；累计额弹一下
          goBtn.textContent = "已奉纳";
          goBtn.classList.remove("busy");
          totalEl.textContent = "已奉纳 " + r.offer_total + " 円";
          totalEl.classList.add("pop");
          setTimeout(function () { totalEl.classList.remove("pop"); }, 320);
          if (orbEl) orbEl.classList.add("still");
          sealEl.hidden = false;
          sealEl.classList.add("stamp");
          // 收场时序（SaisenBox.kt:352-355）：印章落定后 delay(190) 弹累计额、
          // 再 delay(560) 才 onDone+onDismiss —— 合计 750ms，此前写成 1150 偏慢。
          setTimeout(function () { overlay.remove(); }, 750);
        })
        .catch(function (e) {
          locked = false;
          goBtn.textContent = "奉纳";
          goBtn.classList.remove("busy");
          errEl.textContent = e.message;
        });
    });
    setTimeout(function () { amountEl.focus(); }, 60);
  });


  // ── 顶栏 ⋯ 菜单（编辑/删除/复制编号/举报） ───────────────────
  var moreBtn = document.createElement("button");
  moreBtn.className = "top-ic";
  moreBtn.setAttribute("aria-label", "更多");
  moreBtn.innerHTML = '<svg class="ic" viewBox="0 0 24 24"><path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/></svg>';
  var phActions = document.querySelector(".ph-actions");
  if (phActions) phActions.appendChild(moreBtn);
  moreBtn.addEventListener("click", function () {
    var items = [];
    items.push({ label: "复制帖子编号", onClick: function () {
      navigator.clipboard.writeText(detail.dataset.postNo).then(function () { toast("编号已复制：" + detail.dataset.postNo); });
    }});
    items.push({ label: "举报", danger: true, onClick: function () {
      window.confirmDialog({
        title: "举报内容", body: "处理结果会通过社务箱通知您。",
        reasonHint: "举报理由（必填）", confirm: "提交举报",
        onConfirm: function (reason) {
          return window.api("POST", "/api/posts/" + postID + "/report", { reason: reason })
            .then(function () { toast("已提交举报，处理结果会通过社务箱通知您"); });
        },
      });
    }});
    if (mine) items.push({ label: "编辑", onClick: function () { location.href = "/post/" + postID + "/edit"; } });
    if (canDelete) items.push({ label: "删除", danger: true, onClick: function () {
      window.confirmDialog({
        title: "删除帖子", body: "删除后不可恢复。",
        reasonHint: mine ? null : "删除理由（必填）", confirm: "确认删除", danger: true,
        onConfirm: function (reason) {
          return window.api("DELETE", "/api/posts/" + postID, { reason: reason || "" });
        },
      }).then(function (ok) { if (ok) location.href = "/b/main"; });
    }});
  // 宫司及以上：精华 / 置顶（PostDetailScreen.kt:445-489，服务端二次校验）。
  // 注意 data-viewer-rank 是服务端 RoleRank 的 0..4 制（社0/临1/奉2/宫3/神4），
  // 宫司 = 3；安卓侧 Kotlin 是 1..5 制、阈值 4，两套数值不同但语义等价。
  var viewerRank = parseInt(detail.dataset.viewerRank, 10);
  if (!isNaN(viewerRank) && viewerRank >= 3) {
    var isEssence = detail.dataset.essence === "1";
    var isPinned = detail.dataset.pinned === "1";
    items.push({ label: isEssence ? "取消精华" : "设为精华", onClick: function () {
      window.api("POST", "/api/posts/" + postID + "/essence")
        .then(function (r) {
          detail.dataset.essence = r.essence ? "1" : "0";
          toast(r.essence ? "已设为精华" : "已取消精华");
          location.reload();
        })
        .catch(function (e) { toast(e.message); });
    }});
    items.push({ label: isPinned ? "取消置顶" : "置顶", onClick: function () {
      window.api("POST", "/api/posts/" + postID + "/pin")
        .then(function (r) {
          detail.dataset.pinned = r.pinned ? "1" : "0";
          toast(r.pinned ? "已置顶" : "已取消置顶");
          location.reload();
        })
        .catch(function (e) { toast(e.message); });
    }});
  }

  // 菜单里加「生成分享卡片」
  if (detail.dataset.title) {
    items.push({ label: "生成分享卡片", onClick: function () {
      var bodyEl2 = document.querySelector(".md.body") || document.querySelector(".post-content");
      var cv2 = buildShareCard({
        title: detail.dataset.title,
        body: (bodyEl2 ? bodyEl2.innerText : "").replace(/\n{2,}/g, "\n"),
        kind: "帖子", meta: detail.dataset.boardLabel || "",
        id: detail.dataset.postNo || "", author: detail.dataset.author || "",
        time: detail.dataset.time || "",
      });
      cv2.toBlob(function (blob) {
        var url = URL.createObjectURL(blob);
        var a = document.createElement("a");
        a.href = url; a.download = "arisu-" + (detail.dataset.postNo || "post") + ".png";
        document.body.appendChild(a); a.click(); a.remove();
        setTimeout(function () { URL.revokeObjectURL(url); }, 4000);
        toast("分享卡片已生成");
      }, "image/png");
    }});
  }

    window.shrineMenu(moreBtn, items);
  });

  // ── 评论：点赞 / 回复 / ⋯ 菜单 ───────────────────────────────
  var replyTo = null; // {id, authorId, nickname}
  var banner = document.querySelector('[data-role="reply-banner"]');
  var replyName = document.querySelector('[data-role="reply-name"]');
  function setReply(c) {
    replyTo = c;
    if (c) { replyName.textContent = c.nickname; banner.style.display = "flex"; }
    else banner.style.display = "none";
  }
  document.querySelector('[data-role="reply-cancel"]').addEventListener("click", function () { setReply(null); });

  document.querySelectorAll(".comment").forEach(function (node) {
    var cid = node.dataset.cid;
    var mineC = node.dataset.mine === "1";
    var canDel = node.dataset.canDelete === "1";
    var authorRank = parseInt(node.dataset.authorRank || "0", 10);
    var nick = node.querySelector(".name").textContent;
    var avatar = node.querySelector(".avatar");
    var authorNo = avatar ? avatar.getAttribute("href").replace("/u/", "") : "";
    var likeBtn = node.querySelector(".c-like");
    likeBtn.addEventListener("click", function () {
      window.api("POST", "/api/comments/" + cid + "/like").then(function (r) {
        likeBtn.classList.toggle("on", r.liked);
        var c = likeBtn.querySelector('[data-c="like"]');
        c.textContent = r.like_count > 0 ? "顶 " + r.like_count : "顶";
        c.classList.remove("pop"); void c.offsetWidth; c.classList.add("pop");
      }).catch(function (e) { toast(e.message); });
    });
    node.querySelector(".c-reply").addEventListener("click", function () {
      setReply({ id: cid, authorId: authorNo, nickname: nick });
      var input = document.querySelector('[data-role="input"]');
      input.scrollIntoView({ behavior: "smooth" }); input.focus();
    });
    node.querySelector(".c-more").addEventListener("click", function () {
      var moreBtn = node.querySelector(".c-more");
      var items = [{ label: "复制", onClick: function () {
        navigator.clipboard.writeText(node.querySelector(".body").textContent).then(function () { toast("已复制"); });
      }}];
      items.push({ label: "举报", danger: true, onClick: function () {
        window.confirmDialog({
          title: "举报内容", body: "处理结果会通过社务箱通知您。",
          reasonHint: "举报理由（必填）", confirm: "提交举报",
          onConfirm: function (reason) {
            return window.api("POST", "/api/comments/" + cid + "/report", { reason: reason })
              .then(function () { toast("已提交举报，处理结果会通过社务箱通知您"); });
          },
        });
      }});
      if (canDel) items.push({ label: "删除", danger: true, onClick: function () {
        window.confirmDialog({
          title: "删除这条评论？",
          body: mineC ? "删除后不可恢复。" : "删除后不可恢复，将通知该评论作者。",
          reasonHint: mineC ? null : "删除理由（必填）", confirm: "删除", danger: true,
          onConfirm: function (reason) {
            return window.api("DELETE", "/api/comments/" + cid, { reason: reason || "" });
          },
        }).then(function (ok) { if (ok) location.reload(); });
      }});
      window.shrineMenu(moreBtn, items);
    });
  });

  // ── 悬浮评论输入条：表情/贴纸/附图/提及/草稿/发送 ─────────────
  var input = document.querySelector('[data-role="input"]');
  var sendBtn = document.querySelector('[data-role="send"]');
  var fileEl = document.querySelector('[data-role="file"]');
  // 评论草稿：走统一草稿箱（安卓 DraftStore.COMMENT_POST / COMMENT_EMA）
  var isEmaPage = location.pathname.indexOf("/ema/") === 0;
  var draftKind = isEmaPage ? "comment_ema" : "comment_post";
  var draftTarget = (location.pathname.split("/")[2] || "").replace(/^arisu_/, "");
  var draftTitle = document.querySelector(".post-title, .ema-title")?.textContent.trim() || draftTarget;
  var draftImage = null;

  // 草稿恢复（600ms 防抖保存与安卓同口径）
  try {
    var saved = window.drafts.get(draftKind, draftTarget);
    if (saved && (saved.body || saved.imageUrl)) {
      input.value = saved.body || "";
      draftImage = saved.imageUrl || null;
      showImagePreview();
      document.querySelector(".reply-wrap").insertAdjacentHTML("afterbegin",
        '<div class="notice" style="margin:0 auto 8px;max-width:30rem">已恢复上次未发送的草稿</div>');
    }
  } catch (e) { /* 损坏按无草稿 */ }
  var saveTimer = null;
  function saveDraft() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(function () {
      if (!input.value && !draftImage) { window.drafts.remove(draftKind, draftTarget); return; }
      window.drafts.save({
        kind: draftKind, targetId: draftTarget, targetTitle: draftTitle,
        body: input.value, imageUrl: draftImage,
      });
    }, 600);
  }
  input.addEventListener("input", function () {
    // 2000 字硬上限（PostDetailScreen.kt:749：超长的这次输入整条丢弃）
    if (input.value.length > 2000) {
      input.value = input.value.slice(0, 2000);
    }
    saveDraft();
    sendBtn.disabled = input.value.trim() === "";
    syncSize();
  });
  function syncSize() {
    input.style.height = "auto";
    input.style.height = Math.min(input.scrollHeight, 110) + "px";
  }
  sendBtn.disabled = input.value.trim() === "";

  function showImagePreview() {
    var box = document.querySelector('[data-role="img-preview"]');
    if (draftImage) {
      box.querySelector("img").src = draftImage;
      box.style.display = "flex";
    } else box.style.display = "none";
    saveDraft();
  }
  document.querySelector('[data-role="img-remove"]').addEventListener("click", function () {
    draftImage = null; showImagePreview();
  });
  document.querySelector('[data-role="pickimg"]').addEventListener("click", function () {
    fileEl.click();
  });
  fileEl.addEventListener("change", function () {
    var f = fileEl.files[0];
    fileEl.value = "";
    if (!f) return;
    toast("正在寄送图片……");
    window.uploadImage(f).then(function (url) {
      draftImage = url; showImagePreview();
    }).catch(function (e) { toast(e.message); });
  });
  document.querySelector('[data-role="emoji"]').addEventListener("click", function () {
    window.openEmojiSheet(function (v) { insertAtCursor(input, v); }, { stickers: true });
  });
  window.bindMentionTrigger(input, function (pos) {
    window.openMentionPicker(input, pos);
  });

  function insertAtCursor(el, text) {
    var s = el.selectionStart || el.value.length;
    el.value = el.value.slice(0, s) + text + el.value.slice(el.selectionEnd || s);
    var pos = s + text.length;
    el.setSelectionRange(pos, pos);
    el.focus();
    saveDraft(); sendBtn.disabled = el.value.trim() === "";
  }

  // 发送：POST 后刷新列表（服务端渲染的评论结构在响应页里最准）
  sendBtn.addEventListener("click", function () {
    var text = input.value.trim();
    if (!text || sendBtn.disabled) return;
    sendBtn.disabled = true;
    window.api("POST", "/api/posts/" + postID + "/comments", {
      content: text,
      parent_id: replyTo ? parseInt(replyTo.id, 10) : null,
      reply_to_user_id: replyTo && replyTo.authorId ? parseInt(replyTo.authorId.replace("User_", ""), 10) : null,
      image_url: draftImage || "",
    }).then(function () {
      window.drafts.remove(draftKind, draftTarget);
      location.reload();
    }).catch(function (e) {
      sendBtn.disabled = false;
      toast(e.message);
    });
  });
})();

// ══ 神社祝部（data-page="shrine"）：筛选/申请/批阅/检查更新 ══
(function () {
  if (document.body.dataset.page !== "shrine") return;

  // 检查更新（log tab）
  var checkBtn = document.querySelector('[data-role="checkup"]');
  if (checkBtn) checkBtn.addEventListener("click", function () {
    checkBtn.disabled = true;
    var version = (document.querySelector(".cv-num") || {}).textContent || "";
    window.api("GET", "/api/releases/check?version=" + encodeURIComponent(version.replace(/^v/, "")) + "&channel=stable")
      .then(function (r) {
        checkBtn.disabled = false;
        if (r.update_available && r.release) {
          window.confirmDialog({
            title: "发现新版本",
            body: "有栖神社 " + r.release.version + "（" + (r.release.channel === "beta" ? "测试版" : "稳定版") + "）",
            confirm: "立即更新",
            onConfirm: function () {
              var url = r.download_url || "";
              if (url && /^https?:\/\//.test(url)) location.href = url;
              else toast("服务器尚未提供安装包，请联系神主");
              return new Promise(function () { /* 保持弹层直到跳转 */ });
            },
          });
        } else toast("已是最新版本");
      })
      .catch(function () { checkBtn.disabled = false; toast("检查更新失败，请稍后重试"); });
  });

  // 申请担任临时奉仕（staff tab）
  var applyBtn = document.querySelector('[data-role="apply"]');
  if (applyBtn && !applyBtn.disabled) applyBtn.addEventListener("click", function () {
    var overlay = window.shrineSheet(
      '<h3 class="dialog-title">申请担任临时奉仕</h3>' +
      '<p class="form-hint">请简述您的情况，宫司及以上将在一周内审核。</p>' +
      '<div class="form-row"><span class="form-label">申请理由</span>' +
      '<textarea class="field" data-role="reason" maxlength="500" rows="4" placeholder="例如：每天晚上在线 2 小时，可以帮忙看正殿与幻想乡闻的新帖。"></textarea></div>' +
      '<div class="form-row"><span class="form-label">每周可投入时间</span>' +
      '<div class="code-row"><div class="well grow"><select class="field" data-role="effort">' +
      '<option value="">请选择</option><option>1-3 小时</option><option>3-5 小时</option><option>5-10 小时</option><option>10 小时以上</option>' +
      "</select></div></div></div>" +
      '<p class="form-error" data-role="err" style="display:none"></p>' +
      '<button class="btn btn-full" data-role="go">提交</button>'
    );
    overlay.querySelector('[data-role="go"]').addEventListener("click", function () {
      var reason = overlay.querySelector('[data-role="reason"]').value.trim();
      var effort = overlay.querySelector('[data-role="effort"]').value;
      var err = overlay.querySelector('[data-role="err"]');
      if (!reason) { err.textContent = "申请理由不能为空"; err.style.display = "block"; return; }
      if (!effort) { err.textContent = "请选择每周可投入时间"; err.style.display = "block"; return; }
      window.api("POST", "/api/staff/apply", { reason: reason, weekly_hours: effort })
        .then(function () { window.closeSheets(); location.reload(); })
        .catch(function (e) { err.textContent = e.message; err.style.display = "block"; });
    });
  });

  // 批阅申请（宫司+）
  document.querySelectorAll(".pending-row").forEach(function (row) {
    var id = row.dataset.appId;
    var nick = row.dataset.nick;
    row.querySelectorAll("[data-approve]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var approving = btn.dataset.approve === "1";
        window.confirmDialog({
          title: approving ? "通过申请" : "婉拒申请",
          body: approving
            ? "批阅结果会通过社务箱通知对方。附言选填。"
            : "婉拒时建议附上理由，会随结果通知对方。",
          reasonHint: approving ? null : "附言（选填）",
          confirm: approving ? "确认通过" : "确认婉拒",
          onConfirm: function (reason) {
            return window.api("POST", "/api/staff/applications/" + id + "/decide", { approve: approving, reason: reason })
              .then(function () { toast((approving ? "已通过 " : "已婉拒 ") + nick + " 的申请"); });
          },
        }).then(function (ok) { if (ok) location.reload(); });
      });
    });
  });
})();

// ══ 绘马详情（data-page="ema"）：回复/改状态/菜单 ══
(function () {
  if (document.body.dataset.page !== "ema") return;
  var detail = document.querySelector(".ema-detail");
  if (!detail) return;
  var emaID = detail.dataset.emaId;
  var mine = detail.dataset.mine === "1";
  var canDelete = detail.dataset.canDelete === "1";
  var canModerate = detail.dataset.canModerate === "1";

  // 改变状态（奉仕+）
  var stateBtn = document.querySelector('[data-role="state"]');
  if (stateBtn) stateBtn.addEventListener("click", function () {
    var items = ["open", "in_progress", "resolved", "closed"].map(function (s) {
      return { label: "改变状态：" + (s === "in_progress" ? "in progress" : s), onClick: function () {
        window.api("POST", "/api/emas/" + emaID + "/state", { state: s })
          .then(function () { location.reload(); })
          .catch(function (e) { toast(e.message); });
      }};
    });
    window.shrineMenu(stateBtn, items);
  });

  // ⋯ 菜单：举报/编辑/删除
  var moreBtn = document.createElement("button");
  moreBtn.className = "top-ic";
  moreBtn.setAttribute("aria-label", "更多");
  moreBtn.innerHTML = '<svg class="ic" viewBox="0 0 24 24"><path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/></svg>';
  var phActions = document.querySelector(".ph-actions");
  if (phActions) phActions.appendChild(moreBtn);
  moreBtn.addEventListener("click", function () {
    var items = [];
    if (!mine) items.push({ label: "举报", danger: true, onClick: function () {
      window.confirmDialog({
        title: "举报内容", body: "处理结果会通过社务箱通知您。",
        reasonHint: "举报理由（必填）", confirm: "提交举报",
        onConfirm: function (reason) {
          return window.api("POST", "/api/emas/" + emaID + "/report", { reason: reason })
            .then(function () { toast("已提交举报，处理结果会通过社务箱通知您"); });
        },
      });
    }});
    if (mine) items.push({ label: "编辑", onClick: function () { location.href = "/ema/" + emaID + "/edit"; } });
    if (canDelete) items.push({ label: "删除", danger: true, onClick: function () {
      window.confirmDialog({
        title: "删除绘马",
        body: mine ? "删除后不可恢复。" : "删除后不可恢复，删除理由将通知给发起人。",
        reasonHint: mine ? null : "删除理由（必填）", confirm: "确认删除", danger: true,
        onConfirm: function (reason) {
          return window.api("DELETE", "/api/emas/" + emaID, { reason: reason || "" });
        },
      }).then(function (ok) { if (ok) location.href = "/shrine"; });
    }});
    // 生成分享卡片（ShareCard.kt:113-420，与帖子同一套渲染）
    if (detail.dataset.title && window.buildShareCard) {
      items.push({ label: "生成分享卡片", onClick: function () {
        var bodyEl2 = document.querySelector(".ema-body, .md.body");
        var cv2 = window.buildShareCard({
          title: detail.dataset.title,
          body: (bodyEl2 ? bodyEl2.innerText : "").replace(/\n{2,}/g, "\n"),
          kind: "绘马", meta: detail.dataset.stateLabel || "",
          id: detail.dataset.emaNo || "", author: detail.dataset.author || "",
          time: detail.dataset.time || "",
        });
        cv2.toBlob(function (blob) {
          var url = URL.createObjectURL(blob);
          var a = document.createElement("a");
          a.href = url; a.download = "arisu-ema-" + (detail.dataset.emaId || "0") + ".png";
          document.body.appendChild(a); a.click(); a.remove();
          setTimeout(function () { URL.revokeObjectURL(url); }, 4000);
          toast("分享卡片已生成");
        }, "image/png");
      }});
    }

    window.shrineMenu(moreBtn, items);
  });

  // 回复删除
  document.querySelectorAll(".ema-reply").forEach(function (node) {
    var rid = node.dataset.rid;
    var mineR = node.dataset.mine === "1";
    var canDel = node.dataset.canDelete === "1";
    var more = node.querySelector(".body");
    node.addEventListener("click", function (e) {
      if (e.target.closest("a") || e.target.closest("img")) return;
      var items = [];
      if (canDel) items.push({ label: "删除", danger: true, onClick: function () {
        window.confirmDialog({
          title: "删除这条回复？", body: "删除后无法恢复。",
          reasonHint: mineR ? null : "删除理由（必填）", confirm: "删除", danger: true,
          onConfirm: function (reason) {
            return window.api("DELETE", "/api/emas/" + emaID + "/replies/" + rid, { reason: reason || "" })
              .then(function () { toast("回复已删除"); });
          },
        }).then(function (ok) { if (ok) location.reload(); });
      }});
      if (items.length) window.shrineMenu(e.target, items);
    });
  });

  // 回复输入条（表情面板 + 发送）
  var input = document.querySelector('[data-role="input"]');
  var sendBtn = document.querySelector('[data-role="send"]');
  if (!input) return;
  input.addEventListener("input", function () { sendBtn.disabled = input.value.trim() === ""; syncHeight(input); });
  document.querySelector('[data-role="emoji"]').addEventListener("click", function () {
    window.openEmojiSheet(function (v) { insertAtCursor(input, v); }, { stickers: true });
  });
  sendBtn.addEventListener("click", function () {
    var text = input.value.trim();
    if (!text) return;
    sendBtn.disabled = true;
    window.api("POST", "/api/emas/" + emaID + "/replies", { content: text })
      .then(function () { localStorage.removeItem("ema-reply:" + emaID); location.reload(); })
      .catch(function (e) { sendBtn.disabled = false; toast(e.message); });
  });
  // 回复草稿
  input.value = localStorage.getItem("ema-reply:" + emaID) || "";
  sendBtn.disabled = input.value.trim() === "";
  input.addEventListener("input", function () {
    localStorage.setItem("ema-reply:" + emaID, input.value);
  });
  function syncHeight(el) {
    el.style.height = "auto";
    el.style.height = Math.min(el.scrollHeight, 110) + "px";
  }
})();

// ══ 发绘马/编辑绘马（data-page="ema" 且表单存在） ══
(function () {
  var form = document.getElementById("f-ema");
  if (!form) return;
  // 双保险：缺失或 "0" 都算新建（"0" 是 truthy，曾因此发成 PATCH /api/emas/0）
  var emaID = form.dataset.emaId;
  if (!emaID || emaID === "0") emaID = "";
  var title = form.querySelector("input[name=title]");
  var body = form.querySelector("textarea[name=body]");
  var tags = [];
  form.querySelectorAll(".tag-select .tagpill").forEach(function (b) {
    b.addEventListener("click", function () {
      var t = b.dataset.tag;
      var i = tags.indexOf(t);
      if (i >= 0) { tags.splice(i, 1); b.classList.remove("picked"); }
      else { tags.push(t); b.classList.add("picked"); }
    });
  });
  // 编辑模式回填选中标签（由模板在 data-tags 里带）
  var preset = form.dataset.tags;
  if (preset) preset.split(",").forEach(function (t) {
    tags.push(t);
    var b = form.querySelector('[data-tag="' + t + '"]');
    if (b) b.classList.add("picked");
  });
  // markdown 工具条
  bindMarkdownToolbar(form, body);
  // 放弃撰写确认（NewEmaScreen 的 AlertDialog）：
  // 编辑=「放弃修改？」+「尚未保存的修改不会被保留。」
  // 新建=「放弃撰写？」+「尚未发布的内容会留在草稿箱。」
  (function () {
    var back = document.querySelector('[data-role="back"]');
    if (!back) return;
    var dirty = false;
    form.addEventListener("input", function () { dirty = true; });
    back.addEventListener("click", function (e) {
      if (!dirty) return;
      e.preventDefault();
      e.stopImmediatePropagation();
      var editing = !!emaID;
      window.confirmDialog({
        title: editing ? "放弃修改？" : "放弃撰写？",
        body: editing ? "尚未保存的修改不会被保留。" : "尚未发布的内容会留在草稿箱。",
        confirm: "放弃",
        danger: true,
        cancelText: "继续撰写",
        onConfirm: function () {
          if (!editing) {
            window.drafts.save({ kind: "ema", targetId: "new", title: title.value, body: body.value, tags: tags });
          }
          location.href = "/shrine";
          return new Promise(function () {});
        },
      });
    }, true);
  })();

  form.addEventListener("submit", function (ev) {
    ev.preventDefault();
    var err = form.querySelector('[data-role="err"]');
    err.style.display = "none";
    var payload = { title: title.value.trim(), body: body.value, tags: tags };
    var call;
    if (emaID) call = window.api("PATCH", "/api/emas/" + emaID, payload);
    else call = window.api("POST", "/api/emas", payload);
    call.then(function (r) {
      window.drafts.remove("ema", "new");
      location.href = "/ema/" + (emaID || r.id);
    }).catch(function (e) {
      err.textContent = e.message; err.style.display = "block";
    });
  });
  // 草稿（新建模式）：走统一草稿箱（DraftStore.EMA）
  if (!emaID) {
    try {
      var d = window.drafts.get("ema", "new");
      if (d) {
        if (!title.value) title.value = d.title || "";
        if (!body.value) body.value = d.body || "";
        tags.push.apply(tags, d.tags || []);
        tags.forEach(function (t) {
          var b = form.querySelector('[data-tag="' + t + '"]');
          if (b) b.classList.add("picked");
        });
      }
    } catch (e) { /* 忽略 */ }
    // 自动存草稿（NewEmaScreen.kt:157-172）：key 是「标题 + 正文 + 标签」三者，
    // 任一变化都要在 600ms 后落盘；三者全空则删掉草稿，不留空记录。
    var timer = null;
    function scheduleDraft() {
      clearTimeout(timer);
      timer = setTimeout(function () {
        if (!title.value.trim() && !body.value.trim() && !tags.length) {
          window.drafts.remove("ema", "new");
          return;
        }
        window.drafts.save({ kind: "ema", targetId: "new", title: title.value, body: body.value, tags: tags });
      }, 600);
    }
    form.addEventListener("input", scheduleDraft);
    form.addEventListener("change", scheduleDraft);
    // 标签按钮不是 input 事件：点选后手动排一次
    form.querySelectorAll("[data-tag]").forEach(function (b) {
      b.addEventListener("click", scheduleDraft);
    });
  }
})();

// ══ 神社游会（play 页）：求签 / 分享御签 / 保存御签卡 ══
// 行为对齐安卓 PlayScreen.kt：按钮三态（unknown/ready/drawn）由服务端渲染进 data-state、
// 摇签中防重入（busy-orb 契约）、失败文案写进 draw-err 且可点重试（重试先查今日再抽）。
(function () {
  var drawBtn = document.querySelector('[data-role="draw"]');
  if (!drawBtn) return;
  var errEl = document.querySelector('[data-role="draw-err"]');
  var drawLabel = drawBtn.textContent.trim();
  var drawing = false;

  // 吉凶档位与安卓同口径（PlayScreen.kt:1020-1027；阈值与服务端 store.luckBand 一致，文案取中文）
  function luckBand(v) {
    if (v <= 10) return "凶";
    if (v <= 30) return "末吉";
    if (v <= 50) return "小吉";
    if (v <= 70) return "中吉";
    if (v <= 90) return "吉";
    return "大吉";
  }
  function drawnLuck() {
    var el = document.querySelector(".luck-num");
    return el ? parseInt(el.textContent, 10) || 0 : 0;
  }
  function myNickname() {
    // 顶栏/侧栏的用户名都在 DOM 里（topline 移动端隐藏但仍在文档流外可取值）
    var el = document.querySelector(".topline .app-user b, .side-user b");
    return el ? el.textContent.trim() : "";
  }

  // busy-orb 契约：进行中给按钮插 <span class="busy-orb"></span> 并禁用防重复点击；
  // 文案切「摇签中…」对齐安卓（PlayScreen.kt:431），退出时还原按钮原文案
  function setBusy(on) {
    drawing = on;
    drawBtn.disabled = on;
    drawBtn.textContent = on ? "摇签中…" : drawLabel;
    if (on) drawBtn.insertAdjacentHTML("beforeend", '<span class="busy-orb"></span>');
  }
  function showErr(msg) {
    if (!errEl) return;
    errEl.textContent = msg || "加载失败，点击重试";
    errEl.style.display = "block";
  }

  drawBtn.addEventListener("click", function () {
    if (drawing) return;
    setBusy(true);
    window.api("POST", "/api/play/omikuji")
      .then(function () {
        // 结果卡/御签榜/按钮三态都是 play.html 服务端渲染（webPlayPage 一次查全），
        // 页面没有可就地填充的结果区结构——reload 即为「成功后展示结果」，
        // 顺带等价于安卓「抽成功后刷一次御签榜」（PlayScreen.kt:444-446）
        location.reload();
      })
      .catch(function (e) {
        setBusy(false);
        showErr(e.message);
      });
  });

  // 失败文案可点重试，与安卓同序（PlayScreen.kt:469-471）：先查今日是否已中
  //（第一次请求可能已被服务器处理，直接把签领回来），未中再抽
  if (errEl) errEl.addEventListener("click", function () {
    if (drawing) return;
    setBusy(true);
    window.api("GET", "/api/play/omikuji/today")
      .then(function (r) {
        if (r && r.drawn && r.luck > 0) return null;
        return window.api("POST", "/api/play/omikuji");
      })
      .then(function () { location.reload(); })
      .catch(function (e) {
        setBusy(false);
        showErr(e.message);
      });
  });

  // 分享御签：安卓端分享与保存都先弹 FortuneOptionsSheet 选风格/内容，
  // 确认后才导出（PlayScreen.kt:762-790）。网页这里同样先开面板。
  var shareBtn = document.querySelector('[data-role="share"]');
  // 顶栏那枚分享（PlayScreen.kt:386-394）与卡内出口同一动作
  document.querySelectorAll('[data-role="share-fortune"]').forEach(function (b) {
    b.addEventListener("click", function () { if (shareBtn) shareBtn.click(); });
  });
  if (shareBtn) shareBtn.addEventListener("click", function () {
    var luck = drawnLuck();
    if (!luck) return;
    window.openFortuneOptions(luck, "分享御签", function (cv, dateText) {
      var text = "我在有栖神社摇出了今日御签：" + luck + " / 100（" + luckBand(luck) + "），你也来摇一签吧";
      // 能走系统分享就带上卡片图（安卓同语义：分享的是渲染出来的位图）
      cv.toBlob(function (blob) {
        var file = null;
        try { file = new File([blob], "arisu-omikuji.png", { type: "image/png" }); } catch (e) {}
        if (navigator.canShare && file && navigator.canShare({ files: [file] })) {
          navigator.share({ files: [file], title: "有栖神社 · 御神签", text: text });
          return;
        }
        // 退一步：下载卡片 + 复制签文
        downloadCard(cv, "arisu-omikuji-" + dateText + ".png");
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text + " " + location.origin + "/play")
            .then(function () { toast("御签卡已生成，签文已复制"); })
            .catch(function () { toast("御签卡已生成"); });
        } else {
          toast("御签卡已生成");
        }
      }, "image/png");
    });
  });

  // ── 御签卡（FortuneCard.kt:111-490）──────────────────────────
  // 1080×1440、左右 72 边距。三套配色（漆器朱底/纸色/墨色）、六档签文。
  // 版面：头部鸟居+「有栖神社/Arisu Shrine」+右上日期 → 205 细线 →
  // 左侧装订线(60..62, 240..1100) → 「今日御签」小节标 → 右侧鸟居线稿 →
  // 档位 118px → 幸运值 340px +「/ 100」→ 右侧签文两行 + 宜/忌 →
  // 830 细线 →「每日一签 · 明日再来」+ 右上「连续求签 N 天」→ 用户行 →
  // 1050 波浪 → 1140 细线 → 官网/GitHub/右下鸟居 → 1320 御神签·日期
  var PALETTES = {
    LACQUER: { paper: "#9e1f18", ink: "#f7efe2", inkSoft: "rgba(247,239,226,.8)",
      accent: "#d8b36a", line: "rgba(247,239,226,.227)", pattern: "rgba(247,239,226,.227)",
      patternFaint: "rgba(247,239,226,.141)", petal: "rgba(247,239,226,.25)",
      qrInk: "#f7efe2" },
    PAPER: { paper: "#f3ebdd", ink: "#2a241e", inkSoft: "rgba(42,36,30,.8)",
      accent: "#b02a21", line: "rgba(42,36,30,.227)", pattern: "rgba(42,36,30,.18)",
      patternFaint: "rgba(42,36,30,.118)", petal: "rgba(176,42,33,.19)",
      qrInk: "#2a241e" },
    INK: { paper: "#1c1a18", ink: "#f2e9da", inkSoft: "rgba(242,233,218,.8)",
      accent: "#d8b36a", line: "rgba(242,233,218,.227)", pattern: "rgba(242,233,218,.19)",
      patternFaint: "rgba(242,233,218,.118)", petal: "rgba(216,179,106,.25)",
      qrInk: "#d8b36a" },
  };
  var FORTUNE_TEXT = {
    "大吉": { verse: ["云开月满，", "前路自明。"], yi: "赴约", ji: "迟疑" },
    "吉":   { verse: ["风送轻帆，", "一路向晴。"], yi: "远行", ji: "拖延" },
    "中吉": { verse: ["静水行舟，", "渐入佳境。"], yi: "清扫", ji: "熬夜" },
    "小吉": { verse: ["檐下听雨，", "自有清风。"], yi: "读书", ji: "多思" },
    "末吉": { verse: ["守灯待晓，", "后路可期。"], yi: "蓄力", ji: "冒进" },
    "凶":   { verse: ["苍山如海，", "残阳如血。"], yi: "静养", ji: "争执" },
  };
  var PAD = 72, W = 1080, H = 1440;

  function drawToriiLine(g, cx, cy, size, color, lw) {
    // 平面鸟居线稿（FortuneCard.drawTorii）：两柱 + 上下两横 + 中间额束
    g.strokeStyle = color;
    g.lineWidth = lw || 2.5;
    // 安卓 drawTorii(canvas, cx, cy, size)：size 是「宽度」，高约 0.78 倍
    var w = size, h = size * 0.78;
    var left = cx - w / 2, top = cy - h / 2;
    g.beginPath();
    g.moveTo(left, top + h * 0.05);
    g.quadraticCurveTo(cx, top - h * 0.06, left + w, top + h * 0.05);
    g.stroke();
    g.beginPath();
    g.moveTo(left + w * 0.06, top + h * 0.22); g.lineTo(left + w * 0.94, top + h * 0.22);
    g.moveTo(left + w * 0.12, top + h * 0.42); g.lineTo(left + w * 0.88, top + h * 0.42);
    g.moveTo(cx - w * 0.05, top + h * 0.22); g.lineTo(cx - w * 0.05, top + h * 0.42);
    g.moveTo(cx + w * 0.05, top + h * 0.22); g.lineTo(cx + w * 0.05, top + h * 0.42);
    g.moveTo(left + w * 0.18, top + h * 0.42); g.lineTo(left + w * 0.18, top + h);
    g.moveTo(left + w * 0.82, top + h * 0.42); g.lineTo(left + w * 0.82, top + h);
    g.stroke();
  }

  function buildFortuneCard(opt) {
    var style = opt.style || "LACQUER";
    var p = PALETTES[style] || PALETTES.LACQUER;
    var cv = document.createElement("canvas");
    cv.width = W; cv.height = H;
    var g = cv.getContext("2d");
    var serif = '"Arisu Serif","Noto Serif SC","Source Han Serif SC","Songti SC",serif';
    g.fillStyle = p.paper; g.fillRect(0, 0, W, H);

    // 头部
    drawToriiLine(g, 100, 104, 56, p.ink, 2.5);
    g.textAlign = "left";
    g.fillStyle = p.ink; g.font = "600 38px " + serif;
    g.fillText("有栖神社", 170, 120);
    g.fillStyle = p.inkSoft; g.font = "400 21px " + serif;
    g.fillText("Arisu Shrine", 170, 152);
    g.textAlign = "right";
    g.font = "400 23px " + serif;
    g.fillText(opt.date, W - PAD, 124);

    // 205 细线
    g.fillStyle = p.line; g.fillRect(PAD, 205, W - PAD * 2, 1);
    // 左侧装订线
    g.fillStyle = "rgba(247,239,226,.173)";
    if (style === "PAPER") g.fillStyle = "rgba(42,36,30,.173)";
    g.fillRect(60, 240, 2, 860);

    // 小节标「今日御签」：textAlign 必须显式复位（前面刚用过 right）
    g.textAlign = "left";
    g.fillStyle = p.accent; g.fillRect(PAD, 255 - 16, 4, 18);
    g.fillStyle = p.inkSoft; g.font = "400 21px " + serif;
    g.fillText("今日御签", PAD + 15, 255);

    // 右侧鸟居线稿：中心 (850,270)、宽 300 → 占 y 153..387，不压 512 的签文
    drawToriiLine(g, 850, 270, 300, p.pattern, 2.5);
    // 三处花瓣（FortuneCard.kt:135-137）：右上大、右中与左下小
    [[958, 250, 18, p.petal], [1000, 548, 14, p.patternFaint], [152, 960, 16, p.patternFaint]]
      .forEach(function (sp) {
        g.fillStyle = sp[3];
        for (var i = 0; i < 5; i++) {
          var ang = (Math.PI * 2 / 5) * i;
          g.beginPath();
          g.ellipse(sp[0] + Math.cos(ang) * sp[2] * 0.6, sp[1] + Math.sin(ang) * sp[2] * 0.6,
            sp[2] * 0.5, sp[2] * 0.34, ang, 0, Math.PI * 2);
          g.fill();
        }
      });

    // 档位 118px
    g.textAlign = "left";
    g.fillStyle = (opt.band === "大吉") ? p.accent : p.ink;
    g.font = "700 118px " + serif;
    g.fillText(opt.band, PAD, 404);

    // 幸运值 340px
    g.fillStyle = p.ink; g.font = "700 340px " + serif;
    g.fillText(String(opt.luck), PAD, 760);
    var numW = g.measureText(String(opt.luck)).width;
    g.fillStyle = p.inkSoft; g.font = "400 34px " + serif;
    g.fillText("/ 100", PAD + numW + 22, 760);

    // 签文 + 宜忌
    var ft = FORTUNE_TEXT[opt.band];
    if (ft) {
      if (opt.showVerse !== false) {
        g.textAlign = "right";
        g.fillStyle = "rgba(247,239,226,.9)";
        if (style === "PAPER") g.fillStyle = "rgba(42,36,30,.9)";
        g.font = "400 36px " + serif;
        ft.verse.forEach(function (ln, i) { g.fillText(ln, W - PAD, 512 + i * 48); });
      }
      if (opt.showYiJi !== false) {
        g.textAlign = "right";
        g.fillStyle = p.inkSoft; g.font = "400 24px " + serif;
        g.fillText("宜", 936, 664);
        g.fillText("忌", 936, 708);
        g.fillStyle = p.ink; g.font = "600 28px " + serif;
        g.fillText(ft.yi, W - PAD, 664);
        g.fillText(ft.ji, W - PAD, 708);
      }
    }

    // 830 细线 + 说明
    g.fillStyle = p.line; g.fillRect(PAD, 830, W - PAD * 2, 1);
    g.textAlign = "left";
    g.fillStyle = p.inkSoft; g.font = "400 24px " + serif;
    g.fillText("每日一签 · 明日再来", PAD, 884);
    if (opt.showStreak !== false && opt.streak > 0) {
      g.textAlign = "right"; g.font = "400 22px " + serif;
      g.fillText("连续求签 " + opt.streak + " 天", W - PAD, 884);
    }

    // 用户行
    g.textAlign = "left";
    g.fillStyle = p.ink; g.font = "600 28px " + serif;
    g.fillText(opt.nickname || "社员", PAD + 76, 978);
    g.fillStyle = p.inkSoft; g.font = "400 22px " + serif;
    g.fillText(opt.no || "", PAD + 76, 1010);

    // 1050 波浪
    g.strokeStyle = p.patternFaint; g.lineWidth = 1.2;
    for (var x = -30; x < W + 30; x += 60) {
      g.beginPath();
      g.arc(x, 1050, 30, Math.PI, 0);
      g.stroke();
    }

    // 1140 细线 + 页脚
    g.fillStyle = p.line; g.fillRect(PAD, 1140, W - PAD * 2, 1);
    g.fillStyle = p.ink; g.font = "600 22px " + serif;
    g.fillText("官网  arisushrine.com", PAD, 1198);
    g.fillStyle = p.inkSoft; g.font = "400 20px " + serif;
    g.fillText("GitHub  github.com/Wojusensei/Arisu-Shrine", PAD, 1234);
    // 右下角：勾了二维码就印码，否则画鸟居线稿（FortuneCard.kt:469-473）。
    // 码不铺底不描边，卡面自身就是静区（:489）。
    var qr = null;
    if (opt.showQr && typeof window.qrMatrix === "function") {
      try { qr = window.qrMatrix("https://arisushrine.com"); } catch (e) { qr = null; }
    }
    if (qr) {
      var box = 150, cell = Math.max(1, Math.floor(box / qr.length));
      var qsize = cell * qr.length;
      var qleft = W - PAD - qsize, qtop = 1122 + (box - qsize) / 2;
      g.fillStyle = p.qrInk;
      for (var qy = 0; qy < qr.length; qy++) {
        for (var qx = 0; qx < qr.length; qx++) {
          if (!qr[qy][qx]) continue;
          g.fillRect(qleft + qx * cell, qtop + qy * cell, cell, cell);
        }
      }
    } else {
      drawToriiLine(g, 984, 1202, 48, p.inkSoft, 2);
    }

    g.textAlign = "center";
    g.fillStyle = p.inkSoft; g.font = "400 18px " + serif;
    g.fillText("御神签 · " + opt.date, W / 2, 1320);
    return cv;
  }
  window.buildFortuneCard = buildFortuneCard;

  function downloadCard(cv, name) {
    cv.toBlob(function (blob) {
      if (!blob) { toast("御签卡生成失败"); return; }
      var a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = name;
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(function () { URL.revokeObjectURL(a.href); }, 1000);
    }, "image/png");
  }

  // ── 御签卡选项面板（FortuneOptionsSheet.kt）──────────────────
  // 结构：标题「卡面内容」→ 3:4 预览（随风格与开关即时重画）→
  // 「卡面风格」三枚胶囊（朱漆/素纸/墨夜）→「显示内容」四开关
  // （签文/宜忌/连续求签/二维码）→ 确认按钮。
  var cardStyle = "LACQUER";
  var cardOptions = { showVerse: true, showYiJi: true, showStreak: true, showQr: false };

  function openFortuneOptions(luck, confirmText, onConfirm) {
    var d = new Date();
    var pad4 = function (n) { return String(n).padStart(2, "0"); };
    var dateText = d.getFullYear() + "." + pad4(d.getMonth() + 1) + "." + pad4(d.getDate());
    var opts = Object.assign({}, cardOptions);
    var style = cardStyle;
    var overlay = window.shrineSheet(
      '<div class="sheet-head"><span class="sheet-grip"></span><h3>卡面内容</h3></div>' +
      '<div class="fc-preview-wrap"><img class="fc-preview" data-role="fc-preview" alt=""></div>' +
      '<div class="section-label">卡面风格</div>' +
      '<div class="filter-pills" data-role="fc-styles">' +
      '<button class="filter-pill" data-style="LACQUER">朱漆</button>' +
      '<button class="filter-pill" data-style="PAPER">素纸</button>' +
      '<button class="filter-pill" data-style="INK">墨夜</button></div>' +
      '<div class="section-label">显示内容</div>' +
      '<div class="neu-surface entry-card" data-role="fc-toggles">' +
      '<button class="entry" data-opt="showVerse"><span class="lbl">签文</span><span class="switch"></span></button>' +
      '<hr class="hairline">' +
      '<button class="entry" data-opt="showYiJi"><span class="lbl">宜忌</span><span class="switch"></span></button>' +
      '<hr class="hairline">' +
      '<button class="entry" data-opt="showStreak"><span class="lbl">连续求签</span><span class="switch"></span></button>' +
      '<hr class="hairline">' +
      '<button class="entry" data-opt="showQr"><span class="lbl">二维码</span><span class="switch"></span></button>' +
      '</div>' +
      '<button class="btn btn-full" data-role="fc-ok">' + window.escHTML(confirmText) + '</button>'
    );
    var sheet = overlay.querySelector(".sheet") || overlay;
    var preview = sheet.querySelector('[data-role="fc-preview"]');

    function rebuild() {
      var cv = buildFortuneCard({
        luck: luck, band: luckBand(luck), date: dateText,
        nickname: myNickname(), no: "", streak: optStreak(),
        style: style, showVerse: opts.showVerse, showYiJi: opts.showYiJi,
        showStreak: opts.showStreak, showQr: opts.showQr,
      });
      preview.src = cv.toDataURL("image/png");
      sheet.querySelectorAll("[data-style]").forEach(function (b) {
        b.classList.toggle("on", b.dataset.style === style);
      });
      sheet.querySelectorAll("[data-opt]").forEach(function (b) {
        b.querySelector(".switch").classList.toggle("on", !!opts[b.dataset.opt]);
      });
      cardStyle = style; cardOptions = Object.assign({}, opts);
    }
    function optStreak() {
      // 连续天数由服务端页面上带下来的数值决定；没有就 0（与卡片一致）
      var el = document.querySelector("[data-role='streak-days']");
      return el ? (parseInt(el.textContent, 10) || 0) : 0;
    }

    sheet.querySelector("[data-role='fc-styles']").addEventListener("click", function (e) {
      var b = e.target.closest("[data-style]");
      if (!b) return;
      style = b.dataset.style;
      rebuild();
    });
    sheet.querySelector("[data-role='fc-toggles']").addEventListener("click", function (e) {
      var b = e.target.closest("[data-opt]");
      if (!b) return;
      opts[b.dataset.opt] = !opts[b.dataset.opt];
      rebuild();
    });
    sheet.querySelector('[data-role="fc-ok"]').addEventListener("click", function () {
      var cv = buildFortuneCard({
        luck: luck, band: luckBand(luck), date: dateText,
        nickname: myNickname(), no: "", streak: optStreak(),
        style: style, showVerse: opts.showVerse, showYiJi: opts.showYiJi,
        showStreak: opts.showStreak, showQr: opts.showQr,
      });
      window.closeSheets();
      onConfirm(cv, dateText);
    });
    rebuild();
  }
  window.openFortuneOptions = openFortuneOptions;

  // 保存到相册（PlayScreen.kt:291-305 + FortuneCard.save）
  var saveBtn = document.querySelector('[data-role="save"]');
  if (saveBtn) saveBtn.addEventListener("click", function () {
    var luck = drawnLuck();
    if (!luck) return;
    // 先开选项面板（安卓 PlayScreen 保存也走 FortuneOptionsSheet）
    openFortuneOptions(luck, "保存到相册", function (cv, dateText) {
      downloadCard(cv, "arisu-omikuji-" + dateText + ".png");
      toast("已保存到相册");
    });
  });
})();

// ══ 发帖/编辑帖子（data-page="post" 且表单存在） ══
(function () {
  if (document.body.dataset.page !== "post") return;
  var form = document.getElementById("f-post");
  if (!form) return;
  // 双保险：data-post-id 缺失或为 "0" 都算新建（"0" 是 truthy，曾因此发成 PATCH /api/posts/0）
  var postID = form.dataset.postId;
  if (!postID || postID === "0") postID = "";
  var board = form.dataset.board || "main";
  var coverURL = form.dataset.cover || "";
  var titleEl = form.querySelector("input[name=title]");
  var introEl = form.querySelector("input[name=intro]");
  var coverEl = form.querySelector("input[name=cover_url]");
  var bodyEl = form.querySelector("textarea[name=body]");
  var errEl = form.querySelector('[data-role="err"]');
  var hintEl = form.querySelector('[data-role="upload-hint"]');

  // 板块选择
  form.querySelectorAll(".board-select [data-board]").forEach(function (b) {
    if (b.dataset.board === board) b.classList.add("on");
    b.addEventListener("click", function () {
      form.querySelectorAll(".board-select a").forEach(function (x) { x.classList.remove("on"); });
      b.classList.add("on");
    });
  });
  function pickedBoard() {
    var on = form.querySelector(".board-select .on");
    return on ? on.dataset.board : board;
  }

  // markdown 工具条（与绘马表单同一套 toggle 逻辑，见 bindMarkdownToolbar）
  bindMarkdownToolbar(form, bodyEl);

  // 封面上传：选图 → /api/upload → 地址填入
  // 封面（NewPostScreen.kt 封面段）：
  // 未选 → 「选择封面图」；选好 → 「封面预览」缩略图 + 「编辑这张图」「移除这张图」。
  var coverFile = form.querySelector('[data-role="coverfile"]');
  var coverEmpty = form.querySelector('[data-role="cover-empty"]');
  var coverPicked = form.querySelector('[data-role="cover-picked"]');
  var coverThumb = form.querySelector('[data-role="cover-thumb"]');
  var coverURLWell = form.querySelector('[data-role="cover-url-well"]');

  function syncCover() {
    var has = !!coverEl.value;
    if (coverEmpty) coverEmpty.hidden = has;
    if (coverPicked) coverPicked.hidden = !has;
    if (coverURLWell) coverURLWell.hidden = !has;   // 有图才显示可编辑的地址
    if (has && coverThumb) coverThumb.src = coverEl.value;
  }
  syncCover();

  form.querySelector('[data-role="pickcover"]').addEventListener("click", function () { coverFile.click(); });
  // 选图 → 裁剪器（锁 16:9，与帖子详情横幅同形，NewPostScreen.kt:444-448）→ 确定才上传。
  // 取消 = 放弃这次选图，旧封面原样留着（安卓同一口径）。
  function uploadCoverFile(f) {
    hintEl.textContent = "封面寄送中……"; hintEl.style.display = "block";
    window.uploadImage(f).then(function (url) {
      coverEl.value = url;
      hintEl.style.display = "none";
      syncCover();
      toast("封面已就绪");
    }).catch(function (e) { hintEl.textContent = e.message; });
  }
  coverFile.addEventListener("change", function () {
    var f = coverFile.files[0];
    coverFile.value = "";
    if (!f) return;
    window.openImageEditor(f, {
      aspect: 16 / 9,
      onConfirm: function (blob, ext) {
        uploadCoverFile(blob);
      },
    });
  });
  // 「编辑这张图」：拿现有封面重进裁剪器，确定后换成新的上传件
  form.querySelector('[data-role="cover-edit"]').addEventListener("click", function () {
    if (!coverEl.value) return;
    fetch(coverEl.value, { credentials: "omit" })
      .then(function (r) { if (!r.ok) throw new Error("封面取不回来，请重选一张"); return r.blob(); })
      .then(function (blob) {
        window.openImageEditor(blob, {
          aspect: 16 / 9,
          onConfirm: function (out) { uploadCoverFile(out); },
        });
      })
      .catch(function (e) { toast(e.message); });
  });
  // 「移除这张图」：清空封面，回到未选态（并不删服务器上的原图）
  form.querySelector('[data-role="cover-remove"]').addEventListener("click", function () {
    coverEl.value = "";
    syncCover();
  });

  // 正文配图：多张依次上传，占位符 ![图片](上传中-N) → 完成后原位换真地址
  var imgCounter = 0;
  var imgFile = form.querySelector('[data-role="imgfile"]');
  // 插入图片三选一（NewPostScreen.kt:700-760）：从相册选择 / 拍照 / 表情包。
  // 网页里相册与拍照都落到 <input type=file>（手机的 capture 属性负责唤起相机），
  // 表情包复用评论那条 EmojiSheet。
  form.querySelector('[data-role="pickimg"]').addEventListener("click", function () {
    var sheet = window.shrineSheet(
      '<div class="sheet-head"><span class="sheet-grip"></span>' +
      '<h3>插入图片</h3>' +
      '<p class="sheet-sub">图片先上传到神社，再以 Markdown 插入正文</p></div>' +
      '<div class="img-src-list">' +
      '<button class="img-src" data-role="from-album">' +
      '<span class="is-ic"><svg class="ic" viewBox="0 0 24 24"><path d="M22 16V4c0-1.1-.9-2-2-2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2zm-11-4l2.03 2.71L16 11l4 5H8l3-4zM2 6v14c0 1.1.9 2 2 2h14v-2H4V6H2z"/></svg></span>' +
      '<span class="is-main"><b>从相册选择</b><span>一次最多挑 9 张，发前还能裁剪旋转</span></span></button>' +
      '<button class="img-src" data-role="from-camera">' +
      '<span class="is-ic"><svg class="ic" viewBox="0 0 24 24"><path d="M12 15.2a3.2 3.2 0 100-6.4 3.2 3.2 0 000 6.4z"/><path d="M9 2L7.17 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2h-3.17L15 2H9zm3 15c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5z"/></svg></span>' +
      '<span class="is-main"><b>拍照</b><span>调用相机现拍一张</span></span></button>' +
      '<button class="img-src" data-role="from-sticker">' +
      '<span class="is-ic"><svg class="ic" viewBox="0 0 24 24"><path d="M12 22c5.52 0 10-4.48 10-10S17.52 2 12 2 2 6.48 2 12s4.48 10 10 10zm0-18c4.41 0 8 3.59 8 8s-3.59 8-8 8-8-3.59-8-8 3.59-8 8-8zm-5 7c.83 0 1.5-.67 1.5-1.5S7.83 8 7 8s-1.5.67-1.5 1.5S6.17 11 7 11zm10-1.5c0 .83-.67 1.5-1.5 1.5s-1.5-.67-1.5-1.5S14.67 8 15.5 8s1.5.67 1.5 1.5zM12 17.5c2.03 0 3.8-1.11 4.75-2.75l-1.73-.87c-.61 1.02-1.71 1.62-3.02 1.62s-2.41-.6-3.02-1.62l-1.73.87c.95 1.64 2.72 2.75 4.75 2.75z"/></svg></span>' +
      '<span class="is-main"><b>表情包</b><span>从自留表情里挑一张，会动的那种也在</span></span></button>' +
      '</div>'
    );
    sheet.querySelector('[data-role="from-album"]').addEventListener("click", function () {
      sheet.remove(); imgFile.removeAttribute("capture"); imgFile.click();
    });
    sheet.querySelector('[data-role="from-camera"]').addEventListener("click", function () {
      // capture 让手机直接唤起相机；桌面浏览器会退化成普通文件选择
      sheet.remove(); imgFile.setAttribute("capture", "environment"); imgFile.click();
    });
    sheet.querySelector('[data-role="from-sticker"]').addEventListener("click", function () {
      sheet.remove();
      window.openEmojiSheet(function (v) {
        // insertAtCursor 是评论段的局部函数，撰写器这里就地插到光标
        var pos = bodyEl.selectionStart == null ? bodyEl.value.length : bodyEl.selectionStart;
        bodyEl.value = bodyEl.value.slice(0, pos) + v + bodyEl.value.slice(bodyEl.selectionEnd == null ? pos : bodyEl.selectionEnd);
        bodyEl.selectionStart = bodyEl.selectionEnd = pos + v.length;
        bodyEl.focus();
      }, { stickers: true });
    });
  });
  // 每张先过裁剪器（NewPostScreen.kt:444-448），取消即跳过这张
  imgFile.addEventListener("change", function () {
    Array.prototype.forEach.call(imgFile.files, function (f) {
      window.openImageEditor(f, { onConfirm: function (blob) { uploadBodyImage(blob); } });
    });
    imgFile.value = "";
  });
  function uploadBodyImage(f) {
    imgCounter++;
    var ph = "![图片](上传中-" + Date.now() + "-" + imgCounter + ")";
    var s = bodyEl.selectionStart || bodyEl.value.length;
    var needsNL = s > 0 && bodyEl.value[s - 1] !== "\n";
    bodyEl.value = bodyEl.value.slice(0, s) + (needsNL ? "\n" : "") + ph + "\n" + bodyEl.value.slice(s);
    hintEl.textContent = "图片寄送中……"; hintEl.style.display = "block";
    window.uploadImage(f).then(function (url) {
      bodyEl.value = bodyEl.value.replace(ph, "![图片](" + url + ")");
      hintEl.style.display = "none";
    }).catch(function (e) {
      bodyEl.value = bodyEl.value.replace(ph + "\n", "").replace(ph, "");
      hintEl.textContent = e.message;
    });
  }

  // 放弃撰写/修改确认（NewPostScreen.kt:508-529）：
  // 编辑模式问「放弃修改？」，新建问「放弃撰写？」；说明文案也不同
  // （编辑=尚未保存的修改不会被保留；新建=尚未发布的内容会留在草稿箱）。
  // 挂在页头返回钮上；表单没改过就直接退，不打扰。
  (function () {
    var back = document.querySelector('[data-role="back"]');
    if (!back) return;
    var dirty = false;
    form.addEventListener("input", function () { dirty = true; });
    back.addEventListener("click", function (e) {
      if (!dirty) return;                  // 没动过就不拦
      e.preventDefault();
      e.stopImmediatePropagation();
      var editing = !!form.dataset.postId;
      window.confirmDialog({
        title: editing ? "放弃修改？" : "放弃撰写？",
        body: editing ? "尚未保存的修改不会被保留。" : "尚未发布的内容会留在草稿箱。",
        confirm: "放弃",
        danger: true,
        cancelText: "继续编辑",
        onConfirm: function () {
          // 新建时把草稿落一次盘（说明文案承诺「会留在草稿箱」），再退
          if (!editing) {
            window.drafts.save({
              kind: "post", targetId: "new",
              title: titleEl.value, intro: introEl.value, body: bodyEl.value, board: pickedBoard(),
            });
          }
          location.href = "/b/main";
          return new Promise(function () {});   // 让弹窗留在原地等跳转
        },
      });
    }, true);
  })();

  // 提交：新建 POST / 编辑 PATCH
  form.addEventListener("submit", function (ev) {
    ev.preventDefault();
    errEl.style.display = "none";
    var payload = {
      title: titleEl.value.trim(),
      intro: introEl.value.trim(),
      body: bodyEl.value,
      board: pickedBoard(),
      cover: coverEl.value,
    };
    var call;
    if (postID) call = window.api("PATCH", "/api/posts/" + postID, payload);
    else call = window.api("POST", "/api/posts", payload);
    call.then(function (r) {
      window.drafts.remove("post", "new");
      location.href = "/post/" + (r.no || ("arisu_" + String(r.id).padStart(6, "0")));
    }).catch(function (e) {
      errEl.textContent = e.message;
      errEl.style.display = "block";
    });
  });

  // 草稿（新建模式，600ms 防抖；编辑模式不存——线上原文为准）
  // 走统一草稿箱（DraftStore.POST）。
  // 恢复前先问（NewPostScreen.kt:488-506）：「继续编辑草稿？」/「上次的草稿还没有发布。」
  // 继续编辑=预填；重新开始=丢掉。点遮罩按「继续编辑」处理，草稿不能因误触丢。
  var draftTarget = postID || "new";
  if (!postID) {
    try {
      var d = window.drafts.get("post", "new");
      if (d && (d.title || d.body || d.intro)) {
        window.confirmDialog({
          title: "继续编辑草稿？",
          body: "上次的草稿还没有发布。",
          confirm: "继续编辑",
          cancelText: "重新开始",
          /* 「重新开始」走 onCancel：清掉草稿，输入框保持空 */
          onCancel: function () {
            window.drafts.remove("post", "new");
          },
          onConfirm: function () {
            if (!titleEl.value) titleEl.value = d.title || "";
            if (!introEl.value) introEl.value = d.intro || "";
            if (!bodyEl.value) bodyEl.value = d.body || "";
            // 板块：回填变量之外还要同步页签选中态（NewPostScreen.kt:202 的 board 也是 key）
            if (d.board) {
              board = d.board;
              form.querySelectorAll("[data-board]").forEach(function (b) {
                b.classList.toggle("on", b.dataset.board === board);
              });
            }
            // 封面：「封面」也是草稿 key 之一（NewPostScreen.kt:202 含 coverUrl）
            if (d.coverUrl) {
              var coverEl = form.querySelector('[data-role="cover-url-well"] input');
              if (coverEl && !coverEl.value) {
                coverEl.value = d.coverUrl;
                var thumb = form.querySelector('[data-role="cover-thumb"]');
                var row = form.querySelector('[data-role="cover-row"]');
                if (thumb) thumb.src = d.coverUrl;
                if (row) {
                  var empty = row.querySelector('[data-role="cover-empty"]');
                  var picked = row.querySelector('[data-role="cover-picked"]');
                  if (empty) empty.hidden = true;
                  if (picked) picked.hidden = false;
                }
              }
            }
            return Promise.resolve();
          },
        });
      }
    } catch (e) { /* 忽略 */ }
    // 自动存草稿（NewPostScreen.kt:202-215）：key 是「标题 + 介绍语 + 正文 + 板块 + 封面」
    // 五者，任一变化都要在 600ms 后落盘；全空则删草稿。
    var timer = null;
    function schedulePostDraft() {
      clearTimeout(timer);
      timer = setTimeout(function () {
        var coverEl = form.querySelector('[data-role="cover-url-well"] input');
        var cover = coverEl ? coverEl.value.trim() : "";
        if (!titleEl.value.trim() && !introEl.value.trim() && !bodyEl.value.trim() && !cover) {
          window.drafts.remove("post", "new");
          return;
        }
        window.drafts.save({
          kind: "post", targetId: "new",
          title: titleEl.value, intro: introEl.value, body: bodyEl.value,
          board: pickedBoard(), coverUrl: cover,
        });
      }, 600);
    }
    form.addEventListener("input", schedulePostDraft);
    // 板块页签与封面按钮不是 input 事件
    form.querySelectorAll("[data-board]").forEach(function (b) {
      b.addEventListener("click", schedulePostDraft);
    });
    form.addEventListener("click", function (e) {
      if (e.target.closest('[data-role="pickcover"], [data-role="cover-remove"], [data-role="cover-edit"]')) {
        setTimeout(schedulePostDraft, 100);   // 等封面状态先更新
      }
    });
  }
})();

// ══ 他人主页（/u/*）：关注/取关/拉黑/举报/管理菜单 ══
// 页面标记 data-page="user"（webapp_me.go 的 webUserPage 单独给）；此前与
// ══ 拉黑名单：行尾「解除」（BlockedUsersScreen.kt:129-152）══
// 安卓解除后就地从列表移除并弹「已解除拉黑」；网页同样不整页刷新。
(function () {
  var buttons = document.querySelectorAll('[data-role="unblock"][data-id]');
  if (!buttons.length) return;
  Array.prototype.forEach.call(buttons, function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      if (btn.disabled) return;
      btn.disabled = true;
      window.api('DELETE', '/api/users/' + btn.getAttribute('data-id') + '/block')
        .then(function () {
          var row = btn.closest('.blocks-row');
          if (row) row.remove();
          if (!document.querySelector('.blocks-row')) location.reload();
          window.toast('已解除拉黑');
        })
        .catch(function (err) { btn.disabled = false; window.toast(err.message); });
    });
  });
})();

// ══ 关注 / 粉丝列表：行内小胶囊关注钮（FollowListScreen.kt:226-247）══
// 未关注点一下即关注；已关注点一下取消（安卓是同颗胶囊直接 toggle，不弹菜单）。
(function () {
  var buttons = document.querySelectorAll('.follow-act[data-id]');
  if (!buttons.length) return;
  Array.prototype.forEach.call(buttons, function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      if (btn.disabled) return;
      var on = btn.getAttribute('data-following') === '1';
      btn.disabled = true;
      window.api(on ? 'DELETE' : 'POST', '/api/users/' + btn.getAttribute('data-id') + '/follow')
        .then(function () {
          btn.setAttribute('data-following', on ? '0' : '1');
          btn.classList.toggle('on', !on);
          btn.textContent = on ? '关注' : '已关注';
        })
        .catch(function (err) { window.toast(err.message); })
        .then(function () { btn.disabled = false; });
    });
  });
})();

// me.html 共用 "me" 只能靠「有没有 .heat-card」分流，一旦我的客房长出热度图
// 就会把「⋯ 更多」按钮错装到自己的页头上。
(function () {
  if (document.body.getAttribute("data-page") !== "user") return;
  var followBtn = document.querySelector("[data-follow]");
  var userID = followBtn
    ? parseInt(followBtn.getAttribute("data-follow"), 10) || 0
    : parseInt((location.pathname.split("/u/")[1] || "").replace(/^User_/, ""), 10) || 0;
  if (followBtn) followBtn.addEventListener("click", function () {
    // 已关注态点开菜单「取消关注」（对齐 UserProfileScreen.kt:632-643 的 DropdownMenu）
    if (followBtn.getAttribute("data-following") === "1") {
      window.shrineMenu(followBtn, [{ label: "取消关注", danger: true, onClick: function () {
        window.api("DELETE", "/api/users/" + userID + "/follow")
          .then(function () { location.reload(); })
          .catch(function (e) { toast(e.message); });
      }}]);
      return;
    }
    window.api("POST", "/api/users/" + userID + "/follow")
      .then(function () { location.reload(); })
      .catch(function (e) { toast(e.message); });
  });

  // ⋯ 菜单
  var moreBtn = document.createElement("button");
  moreBtn.className = "top-ic";
  moreBtn.setAttribute("aria-label", "更多");
  moreBtn.innerHTML = '<svg class="ic" viewBox="0 0 24 24"><path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/></svg>';
  var phActions = document.querySelector(".ph-actions");
  if (phActions) phActions.appendChild(moreBtn);
  moreBtn.addEventListener("click", function () {
    var caps = document.querySelector('[data-role="user-caps"]');
    var cd = caps ? caps.dataset : {};
    var nick = cd.nick || "对方";
    var isSelf = cd.self === "1";
    var items = [];
    // 顺序照 UserProfileScreen.kt:201-292：
    // 禁言 → 警告 → 封禁/解除封禁 → 调整身份 → 拉黑/解除拉黑 → 复制主页链接 → 举报
    if (!isSelf && cd.canCurate === "1") {
      items.push({ label: "禁言", onClick: function () { openMuteSheet(userID, nick); } });
    }
    if (!isSelf && cd.canModerate === "1") {
      items.push({ label: "警告", onClick: function () { openWarnSheet(userID, nick); } });
    }
    if (!isSelf && cd.canCurate === "1") {
      var banned = cd.targetBanned === "1";
      items.push({ label: banned ? "解除封禁" : "封禁", danger: true, onClick: function () {
        window.confirmDialog({
          title: banned ? "解除封禁" : "封禁社员",
          body: banned ? "解除后对方可以正常登录使用。" : "封禁后对方将无法登录，处理结果会通过社务箱通知。",
          reasonHint: banned ? null : "封禁理由（必填）",
          confirm: banned ? "确认解除" : "确认封禁",
          danger: !banned,
          onConfirm: function (reason) {
            return window.api("POST", "/api/users/" + userID + (banned ? "/unban" : "/ban"),
              banned ? {} : { reason: reason })
              .then(function () { toast(banned ? "已解除封禁" : "已发送至对方社务箱"); location.reload(); });
          },
        });
      }});
    }
    if (!isSelf && cd.canAssign === "1") {
      items.push({ label: "调整身份", onClick: function () { openRoleSheet(userID, nick, cd.curRole); } });
    }
    var blocked = cd.blocked === "1";
    items.push({ label: blocked ? "解除拉黑" : "拉黑", danger: !blocked, onClick: function () {
      if (blocked) {
        window.api("DELETE", "/api/users/" + userID + "/block")
          .then(function () { toast("已解除拉黑"); location.reload(); })
          .catch(function (e) { toast(e.message); });
        return;
      }
      window.confirmDialog({
        title: "拉黑", body: "拉黑后您将看不到对方的内容与通知，对方也无法关注或打扰您。",
        confirm: "拉黑", danger: true,
        onConfirm: function () {
          return window.api("POST", "/api/users/" + userID + "/block")
            .then(function () { toast("已拉黑"); location.reload(); });
        },
      });
    }});
    items.push({ label: "复制主页链接", onClick: function () {
      var link = location.origin + "/u/User_" + String(userID).padStart(6, "0");
      if (navigator.clipboard) {
        navigator.clipboard.writeText(link).then(function () { toast("链接已复制"); })
          .catch(function () { toast(link); });
      } else { toast(link); }
    }});
    items.push({ label: "举报", danger: true, onClick: function () {
      window.confirmDialog({
        title: "举报社员", body: "处理结果会通过社务箱通知您。",
        reasonHint: "举报理由（必填）", confirm: "提交举报",
        onConfirm: function (reason) {
          return window.api("POST", "/api/users/" + userID + "/report", { reason: reason })
            .then(function () { toast("已提交举报，处理结果会通过社务箱通知您"); });
        },
      });
    }});

    window.shrineMenu(moreBtn, items);
  });
})();

// ══ 设置页（data-page="me" 且改密码弹窗存在） ══
(function () {
  var pwBtn = document.querySelector('[data-role="pw"]');
  if (!pwBtn) return;
  pwBtn.addEventListener("click", function () {
    var overlay = window.shrineSheet(
      '<h3 class="dialog-title">改密码</h3>' +
      '<div class="form-row"><span class="form-label">旧密码</span>' +
      '<div class="well"><input class="field" type="password" data-role="old" placeholder="旧密码"></div></div>' +
      '<div class="form-row"><span class="form-label">新密码</span>' +
      '<div class="well"><input class="field" type="password" data-role="new1" placeholder="新密码（至少 8 位）"></div></div>' +
      '<div class="form-row"><span class="form-label">确认新密码</span>' +
      '<div class="well"><input class="field" type="password" data-role="new2" placeholder="确认新密码"></div></div>' +
      '<p class="form-error" data-role="err" style="display:none"></p>' +
      '<button class="btn btn-full" data-role="go">确认</button>'
    );
    overlay.querySelector('[data-role="go"]').addEventListener("click", function () {
      var old = overlay.querySelector('[data-role="old"]').value;
      var n1 = overlay.querySelector('[data-role="new1"]').value;
      var n2 = overlay.querySelector('[data-role="new2"]').value;
      var err = overlay.querySelector('[data-role="err"]');
      if (n1 !== n2) { err.textContent = "两次输入的新密码不一致"; err.style.display = "block"; return; }
      if (n1.length < 8) { err.textContent = "新密码至少 8 位"; err.style.display = "block"; return; }
      window.api("PATCH", "/api/me/password", { old_password: old, new_password: n1 })
        .then(function (r) {
          window.closeSheets();
          toast("密码已修改");
          if (r.access_token) {
            // 服务端已吊销其他设备，当前设备拿到新 token 刷新页面即可
            setTimeout(function () { location.reload(); }, 800);
          }
        })
        .catch(function (e) { err.textContent = e.message; err.style.display = "block"; });
    });
  });

  // 注销（两段确认）
  var deactBtn = document.querySelector('[data-role="deact1"]');
  if (deactBtn) deactBtn.addEventListener("click", function () {
    window.confirmDialog({
      title: "注销账号", body: "注销后帖子保留，冷静期 24 小时，期间登录即可取消。",
      confirm: "继续注销", danger: true,
      onConfirm: function () {
        return window.confirmDialog({
          title: "二次确认", body: "请输入「注销」二字以确认",
          reasonHint: "注销", confirm: "注销账号", danger: true,
          onConfirm: function (text) {
            if (text !== "注销") throw new Error("请输入「注销」二字以确认");
            return window.api("POST", "/api/me/deactivate")
              .then(function () { location.href = "/login"; });
          },
        });
      },
    });
  });
})();

// ══ 编辑资料（data-page="me" 且编辑表单存在） ══
(function () {
  var form = document.getElementById("f-profile");
  if (!form) return;
  // 草稿：头像地址与开关先在本地攒着，点「保存」才与其余字段一起 PATCH
  window.profileDraft = {};
  form.addEventListener("submit", function (ev) {
    ev.preventDefault();
    var err = form.querySelector('[data-role="err"]');
    err.style.display = "none";
    var payload = {
      nickname: form.querySelector("input[name=nickname]").value.trim(),
      signature: form.querySelector("textarea[name=signature]").value,
      status_emoji: form.querySelector("input[name=status_emoji]").value,
      status_text: form.querySelector("input[name=status_text]").value,
      blog_url: form.querySelector("input[name=blog]").value.trim(),
      custom_markdown: form.querySelector("textarea[name=custom_md]").value,
      custom_css: form.querySelector("textarea[name=custom_css]").value,
    };
    // 双头像与活跃开关走草稿：只有点「保存」才随这一次 PATCH 一起提交
    var draft = window.profileDraft || {};
    ["fixed_avatar_url", "active_avatar_url"].forEach(function (k) {
      if (draft[k]) payload[k] = draft[k];
    });
    if (typeof draft.active_avatar_enabled === "boolean") payload.active_avatar_enabled = draft.active_avatar_enabled;
    window.api("PATCH", "/api/me", payload)
      .then(function () { location.href = "/me"; })
      .catch(function (e) {
        if (saveBtn) { saveBtn.disabled = false; saveBtn.textContent = "保存"; }
        err.textContent = e.message; err.style.display = "block";
      });
  });

  // ── 未保存退出提醒（EditProfileScreen.kt:145-158）──────────────
  // dirty 不止看文字输入：头像草稿与「启用活跃头像」开关也算（安卓逐项与 meState 比对）。
  var profDirty = false;
  function markProfDirty() { profDirty = true; }
  form.addEventListener("input", markProfDirty);
  form.addEventListener("change", markProfDirty);
  // 头像行 / emoji 芯片 / 活跃开关不是 input 事件：用捕获阶段在 form 内兜住
  form.addEventListener("click", function (e) {
    if (e.target.closest("[data-role='toggle-active-avatar'], .emoji-chip, [data-role='pick-fixed'], [data-role='pick-active']")) {
      markProfDirty();
    }
  }, true);
  (function () {
    // 返回钮在页头（form 外）：类名是 top-ic back，也可能是 .ph-back
    var back = document.querySelector("a.top-ic.back, .ph-back, .page-head .back");
    if (!back) return;
    back.addEventListener("click", function (e) {
      if (!profDirty) return;
      e.preventDefault();
      e.stopImmediatePropagation();
      window.confirmDialog({
        title: "放弃修改？",
        body: "尚未保存的修改不会被保留。",
        confirm: "放弃",
        danger: true,
        cancelText: "继续编辑",
        onConfirm: function () { location.href = "/me"; return new Promise(function () {}); },
      });
    }, true);
  })();

  // ── 双头像（EditProfileScreen.kt:320-355）──────────────────────
  // 点行选图 → 上传 → PATCH fixed_avatar_url / active_avatar_url。
  // 上传失败就地 toast，不整页刷新，避免丢掉正在填的表单。
  var avFile = document.querySelector('[data-role="avatar-file"]');
  var avTarget = null;
  if (avFile) {
    document.querySelector('[data-role="pick-fixed"]').addEventListener("click", function () {
      avTarget = "fixed"; avFile.click();
    });
    document.querySelector('[data-role="pick-active"]').addEventListener("click", function () {
      avTarget = "active"; avFile.click();
    });
    // 选图 → 圆形裁剪（AvatarCropOverlay）→ 确定才上传；取消就什么都不动
    avFile.addEventListener("change", function () {
      var f = avFile.files && avFile.files[0];
      avFile.value = "";
      if (!f || !avTarget) return;
      var target = avTarget;
      window.openImageEditor(f, {
        circle: true,
        onConfirm: function (blob) {
          toast("正在寄送头像……");
          window.uploadImage(blob).then(function (url) {
            var body = {};
            body[target === "fixed" ? "fixed_avatar_url" : "active_avatar_url"] = url;
            // 只落草稿：安卓在「保存」之前不动资料（EditProfileScreen.kt:104-108）
            var prev = document.querySelector('[data-role="' + target + '-preview"]');
            if (prev) prev.innerHTML = '<img src="' + window.escHTML(url) + '" alt="">';
            if (window.profileDraft) window.profileDraft[target === "fixed" ? "fixed_avatar_url" : "active_avatar_url"] = url;
          }).catch(function (e) { toast(e.message); });
        },
      });
    });

    // 状态 emoji 行（EditProfileScreen.kt:382-403）：选中即写 hidden 字段，再点一次取消
    var emojiRow = document.querySelector('[data-role="emoji-row"]');
    var emojiField = document.querySelector('[data-role="status-emoji"]');
    if (emojiRow && emojiField) {
      emojiRow.addEventListener("click", function (e) {
        var chip = e.target.closest(".emoji-chip");
        if (!chip) return;
        var on = chip.classList.contains("on");
        emojiRow.querySelectorAll(".emoji-chip").forEach(function (x) { x.classList.remove("on"); });
        if (on) { emojiField.value = ""; return; }
        chip.classList.add("on");
        emojiField.value = chip.dataset.emoji;
      });
    }
    // 字数计数（CounteredField：签名 50 / 状态文字 10）
    var sig = document.querySelector('#f-profile textarea[name=signature]');
    var sigC = document.querySelector('[data-role="signature-counter"]');
    if (sig && sigC) {
      var syncSig = function () { sigC.textContent = sig.value.length + "/50"; };
      sig.addEventListener("input", syncSig); syncSig();
    }
    var stx = document.querySelector('#f-profile input[name=status_text]');
    var stxC = document.querySelector('[data-role="status-counter"]');
    if (stx && stxC) {
      var syncStx = function () { stxC.textContent = stx.value.length + "/10"; };
      stx.addEventListener("input", syncStx); syncStx();
    }

    // 活跃头像开关（:342-352）：乐观翻转，失败回滚
    var swBtn = document.querySelector('[data-role="toggle-active-avatar"]');
    if (swBtn) {
      var swBusy = false;
      swBtn.addEventListener("click", function () {
        if (swBusy) return;
        swBusy = true;
        var before = swBtn.dataset.on === "1";
        var next = !before;
        swBtn.dataset.on = next ? "1" : "0";
        var sw = swBtn.querySelector(".switch");
        if (sw) sw.classList.toggle("on", next);
        // 只落草稿（EditProfileScreen.kt:342-352 同一份草稿），保存时统一提交
        if (window.profileDraft) window.profileDraft.active_avatar_enabled = next;
        var hint = document.querySelector('[data-role="active-avatar-hint"]');
        if (hint) hint.hidden = next;
        swBusy = false;
      });
    }
  }

  // ── 换绑邮箱（EditProfileScreen.kt:570-600）────────────────────
  // 两步：向新邮箱发验证码 → 输码确认。60s 冷却与安卓一致。
  var rebind = document.querySelector('[data-role="rebind-email"]');
  if (rebind) rebind.addEventListener("click", function () {
    var overlay = window.shrineSheet(
      '<div class="sheet-head"><span class="sheet-grip"></span><h3>换绑邮箱</h3>' +
      '<p class="sheet-sub">验证码将寄到新邮箱；换绑后请用新邮箱登录。</p></div>' +
      '<div class="form-row"><span class="form-label">新邮箱</span>' +
      '<div class="well"><input class="field" data-role="new-email" type="email" placeholder="新邮箱地址"></div></div>' +
      '<div class="form-row"><span class="form-label">验证码</span>' +
      '<div class="code-row"><div class="well grow"><input class="field" data-role="code" inputmode="numeric" maxlength="6" placeholder="6 位数字"></div>' +
      '<button class="btn-ghost" data-role="send">发送验证码</button></div></div>' +
      '<p class="form-error" data-role="err" style="display:none"></p>' +
      '<button class="btn btn-full" data-role="ok">确认换绑</button>'
    );
    var sheet = overlay.querySelector(".sheet") || overlay;
    var q = function (r) { return sheet.querySelector('[data-role="' + r + '"]'); };
    var sendBtn = q("send"), left = 0, timer = null;
    function tick() {
      if (left <= 0) { sendBtn.disabled = false; sendBtn.textContent = "发送验证码"; return; }
      sendBtn.disabled = true; sendBtn.textContent = "重发（" + left + "s）";
      left--; timer = setTimeout(tick, 1000);
    }
    sendBtn.addEventListener("click", function () {
      var em = (q("new-email").value || "").trim();
      var err = q("err");
      err.style.display = "none";
      if (!em) { err.textContent = "请填写新邮箱"; err.style.display = "block"; return; }
      sendBtn.disabled = true;
      window.api("POST", "/api/me/email/change-request", { new_email: em })
        .then(function () { left = 60; tick(); })
        .catch(function (e) {
          sendBtn.disabled = false;
          err.textContent = e.message; err.style.display = "block";
        });
    });
    q("ok").addEventListener("click", function () {
      var err = q("err");
      err.style.display = "none";
      var em = (q("new-email").value || "").trim();
      var code = (q("code").value || "").trim();
      if (!em || !code) { err.textContent = "请填写新邮箱与验证码"; err.style.display = "block"; return; }
      this.disabled = true;
      window.api("POST", "/api/me/email/change", { new_email: em, code: code })
        .then(function () { toast("邮箱已换绑"); location.reload(); })
        .catch(function (e) { err.textContent = e.message; err.style.display = "block"; });
    });
  });
})();

// ══ 收藏夹管理（CollectionsScreen.kt:130-380） ══
(function () {
  var createBtn = document.querySelector('[data-role="create-coll"]');
  if (!createBtn) return;
  var input = document.querySelector('[data-role="new-coll"]');
  var errEl = document.querySelector('[data-role="coll-err"]');
  // 「新建」入口与取消：安卓是 AlertDialog（:231-270），网页用同一套弹层语义
  var dlg = document.querySelector('[data-role="new-coll-dialog"]');
  var openBtn = document.querySelector('[data-role="new-coll-open"]');
  // Android 的确认钮 enabled = newName.isNotBlank()（CollectionsScreen.kt:241），
  // 空白名不是「点了没反应」，而是按钮本身就灰着
  function syncCreateEnabled() { createBtn.disabled = !input.value.trim(); }
  syncCreateEnabled();
  input.addEventListener("input", syncCreateEnabled);
  if (openBtn && dlg) {
    openBtn.addEventListener("click", function () {
      dlg.hidden = false;
      errEl.style.display = "none";
      syncCreateEnabled();
      input.focus();
    });
    dlg.querySelector('[data-role="new-coll-cancel"]').addEventListener("click", function () {
      dlg.hidden = true;
      input.value = "";
    });
    // 空白处点击等同于取消
    dlg.addEventListener("click", function (e) { if (e.target === dlg) { dlg.hidden = true; input.value = ""; } });
  }
  createBtn.addEventListener("click", function () {
    var name = input.value.trim();
    if (!name) return;
    createBtn.disabled = true;
    window.api("POST", "/api/me/collections", { name: name })
      .then(function () { if (dlg) dlg.hidden = true; location.reload(); })
      .catch(function (e) {
        syncCreateEnabled();
        errEl.textContent = e.message; errEl.style.display = "block";
      });
  });
  document.querySelectorAll('[data-role="del-coll"]').forEach(function (btn) {
    btn.addEventListener("click", function () {
      var name = btn.dataset.name;
      window.confirmDialog({
        title: "删除这个收藏夹？",
        body: "「" + name + "」里的帖子会移回默认收藏夹。",
        confirm: "删除", danger: true,
        onConfirm: function () {
          return window.api("DELETE", "/api/me/collections/" + btn.dataset.id);
        },
      }).then(function (ok) { if (ok) location.reload(); });
    });
  });
})();

// ══ 表情包管理（data-page="me" 且存在表情网格） ══
(function () {
  var grid = document.querySelector('[data-role="sticker-grid"]');
  if (!grid) return;
  var fileEl = document.querySelector('[data-role="sticker-file"]');
  var addBtn = document.querySelector('[data-role="add-sticker"]');
  var saveBtn = document.querySelector('[data-role="save-order"]');
  var limit = addBtn ? parseInt(addBtn.dataset.limit, 10) || 0 : 0;
  // dirty：顺序动过才允许保存（安卓 StickerSettingsOverlay 的 enabled = !saving && dirty）
  var dirty = false;
  function markDirty() {
    if (dirty || !saveBtn) return;
    dirty = true;
    saveBtn.disabled = false;
  }
  function savedNow() {
    dirty = false;
    if (saveBtn) saveBtn.disabled = true;
  }

  // 「添加」格：满了给提示（StickerSettingsOverlay.kt:207-212），否则开相册
  if (addBtn) {
    addBtn.addEventListener("click", function () {
      // 删除钮也带 data-id，必须限定格子本身，否则会按两倍计数提前报满
      var count = grid.querySelectorAll(".sticker-cell[data-id]").length;
      if (limit && count >= limit) {
        toast("表情夹最多 " + limit + " 张，先删几张再加");
        return;
      }
      fileEl.click();
    });
  }
  fileEl.addEventListener("change", function () {
    var f = fileEl.files[0];
    fileEl.value = "";
    if (!f) return;
    toast("正在寄送表情……");
    // GIF 原样上传保动图；其余压缩交给服务端
    window.uploadImage(f).then(function (url) {
      return window.api("POST", "/api/stickers", { url: url });
    }).then(function () {
      toast("已收进表情夹");
      location.reload();
    }).catch(function (e) { toast(e.message); });
  });
  document.querySelectorAll('[data-role="del-sticker"]').forEach(function (btn) {
    btn.addEventListener("click", function (ev) {
      ev.stopPropagation();
      window.confirmDialog({
        title: "删除这张表情？",
        body: "只从您的表情夹里拿走，已经发出去的内容不受影响。",
        confirm: "删除", danger: true,
        onConfirm: function () {
          return window.api("DELETE", "/api/stickers/" + btn.dataset.id);
        },
      }).then(function (ok) { if (ok) location.reload(); });
    });
  });

  // 拖动排序（HTML5 DnD）：松手后进 dirty，点保存才落库（安卓同款）
  var cells = Array.prototype.slice.call(grid.querySelectorAll(".sticker-cell[data-id]"));
  var dragEl = null;
  cells.forEach(function (cell) {
    cell.addEventListener("dragstart", function () { dragEl = cell; cell.classList.add("dragging"); });
    cell.addEventListener("dragend", function () {
      dragEl = null;
      cell.classList.remove("dragging");
      markDirty();
    });
    cell.addEventListener("dragover", function (ev) {
      ev.preventDefault();
      if (!dragEl || dragEl === cell) return;
      var r = cell.getBoundingClientRect();
      var after = ev.clientX > r.left + r.width / 2;
      grid.insertBefore(dragEl, after ? cell.nextSibling : cell);
    });
  });

  function currentIds() {
    return Array.prototype.slice.call(grid.querySelectorAll(".sticker-cell[data-id]"))
      .map(function (c) { return parseInt(c.dataset.id, 10); });
  }
  function doSave() {
    var ids = currentIds();
    if (!ids.length) return Promise.resolve();
    return window.api("POST", "/api/stickers/reorder", { ids: ids });
  }
  if (saveBtn) {
    saveBtn.addEventListener("click", function () {
      if (saveBtn.disabled) return;
      saveBtn.disabled = true;
      saveBtn.textContent = "保存中…";
      doSave()
        .then(function () { toast("顺序已保存"); savedNow(); })
        .catch(function (e) { saveBtn.disabled = false; toast(e.message); })
        .then(function () { saveBtn.textContent = "保存"; });
    });
  }

  // 退出确认（StickerSettingsOverlay.kt:148-168）：顺序未保存时拦截返回
  var exitModal = document.querySelector('[data-role="sticker-exit-modal"]');
  if (exitModal && saveBtn) {
    var leave = function () { window.location.href = "/me"; };
    var openExit = function () { exitModal.hidden = false; };
    exitModal.querySelector('[data-role="sticker-exit-cancel"]')
      .addEventListener("click", function () { exitModal.hidden = true; });
    exitModal.querySelector('[data-role="sticker-exit-discard"]')
      .addEventListener("click", function () { dirty = false; leave(); });
    exitModal.querySelector('[data-role="sticker-exit-save"]')
      .addEventListener("click", function () {
        doSave().then(function () { dirty = false; leave(); })
          .catch(function (e) { exitModal.hidden = true; toast(e.message); });
      });
    exitModal.addEventListener("click", function (e) {
      if (e.target === exitModal) exitModal.hidden = true;
    });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && !exitModal.hidden) exitModal.hidden = true;
    });
    // 顶栏返回钮与页头链接都先过确认
    var back = document.querySelector(".ph-back, .page-head .back, a.top-ic.back");
    if (back) {
      back.addEventListener("click", function (e) {
        if (!dirty) return;
        e.preventDefault();
        openExit();
      });
    }
  }
})();

// ══ 我的客房：个人御守弹窗（me.html 服务端渲染好的隐藏块，只做显隐） ══
// 对齐安卓 MyRoomScreen.kt:323-328/716-752：点入口开弹窗、遮罩/关闭钮/返回键（Esc）关。
// 安卓开弹窗也不调任何「标记已读」API——成就数据随页面数据带全，纯展示。
(function () {
  var openBtn = document.querySelector('[data-role="achievements"]');
  var modal = document.querySelector('[data-role="achievements-modal"]');
  if (!openBtn || !modal) return;
  var closeBtn = modal.querySelector('[data-role="ach-close"]');
  var box = modal.querySelector(".modal-box");
  function open() {
    modal.hidden = false; // .modal[hidden]{display:none}，去 hidden 即上浮显示
    if (box) { box.offsetHeight; box.focus(); } // 先逼一次布局，刚取消 hidden 的节点否则聚焦不上
  }
  function close() {
    modal.hidden = true;
    openBtn.focus();
  }
  openBtn.addEventListener("click", open);
  if (closeBtn) closeBtn.addEventListener("click", close);
  modal.addEventListener("click", function (e) {
    if (e.target === modal) close(); // 点遮罩关闭＝安卓 AlertDialog onDismissRequest
  });
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && !modal.hidden) close(); // 返回键
  });
})();

// ══ 参拜热度：默认滚到最右（Heatmap.kt:71-75：最新一周在最右边） ══
(function () {
  var card = document.querySelector(".heat-card");
  if (!card) return;
  card.scrollLeft = card.scrollWidth;
})();

// ══ 我的客房：折叠帖子列表（CollapsiblePostList.kt:40-108） ══
// 默认三条 → 「展开查看更多（共 N 篇）」→ 每页五条 + 上一页/第 x / y 页/下一页 → 「收起」。
// 展开状态与页码不跨页面保存：网页每次导航都是新的 SSR 文档，安卓的 rememberSaveable
// 在这里没有对应物，未展开即等于初始态。
(function () {
  var list = document.querySelector('[data-role="coll-list"]');
  var ctrl = document.querySelector('[data-role="coll-ctrl"]');
  if (!list || !ctrl) return;
  var cards = Array.prototype.slice.call(list.querySelectorAll(":scope > .post-card"));
  var pageSize = 5;
  var expanded = false;
  var page = 0;
  var totalPages = Math.max(1, Math.ceil(cards.length / pageSize));
  var expandBtn = ctrl.querySelector('[data-role="coll-expand"]');
  var pager = ctrl.querySelector(".coll-pager");
  var prevBtn = ctrl.querySelector('[data-role="coll-prev"]');
  var nextBtn = ctrl.querySelector('[data-role="coll-next"]');
  var pageNo = ctrl.querySelector(".coll-page-no");
  var foldBtn = ctrl.querySelector('[data-role="coll-fold"]');
  if (!expandBtn) return;

  function render() {
    // 只对「真正的帖子卡」计数：容器里还有空白文本节点，且 .post-card 的
    // display 是 flex —— 用 el.hidden 会被 .post-card{display:flex} 的优先级压过，
    // 必须走 [hidden] 显式规则（见 app.css 的 .coll-list > .post-card[hidden]）。
    cards.forEach(function (el) { el.hidden = true; });
    var from = expanded ? page * pageSize : 0;
    var to = expanded ? from + pageSize : Math.min(3, cards.length);
    cards.slice(from, to).forEach(function (el) { el.hidden = false; });
    // 折叠态：只在超过三条时给展开入口
    expandBtn.hidden = expanded || cards.length <= 3;
    pager.hidden = !(expanded && totalPages > 1);
    foldBtn.hidden = !expanded;
    prevBtn.disabled = page <= 0;
    nextBtn.disabled = page >= totalPages - 1;
    if (pageNo) pageNo.textContent = "第 " + (page + 1) + " / " + totalPages + " 页";
  }

  expandBtn.addEventListener("click", function () { expanded = true; page = 0; render(); });
  prevBtn.addEventListener("click", function () { if (page > 0) { page--; render(); } });
  nextBtn.addEventListener("click", function () { if (page < totalPages - 1) { page++; render(); } });
  foldBtn.addEventListener("click", function () { expanded = false; page = 0; render(); });
  render();
})();

// ══ 我的客房：固定头像/活跃头像开关（MyRoomScreen.kt:591-599 乐观翻转） ══
(function () {
  var btn = document.querySelector('[data-role="toggle-avatar"]');
  if (!btn) return;
  var sw = btn.querySelector(".switch");
  var busy = false;
  btn.addEventListener("click", function () {
    if (busy) return;
    busy = true;
    var before = btn.getAttribute("data-on") === "1";
    var next = !before;
    // 乐观翻转：滑杆立刻动，失败回滚（安卓同一手法，MyRoomScreen.kt:468-484）
    btn.setAttribute("data-on", next ? "1" : "0");
    if (sw) sw.classList.toggle("on", next);
    var valEl = btn.querySelector('[data-role="avatar-state"]');
    if (valEl) valEl.textContent = next ? "已启用" : "已关闭";
    window.api("PATCH", "/api/me", { active_avatar_enabled: next })
      .then(function () { busy = false; })
      .catch(function (e) {
        btn.setAttribute("data-on", before ? "1" : "0");
        if (sw) sw.classList.toggle("on", before);
        if (valEl) valEl.textContent = before ? "已启用" : "已关闭";
        busy = false;
        toast(e.message);
      });
  });
})();

// ══ 举报处置（reports 页）：处理 / 驳回（对齐安卓 ReportsScreen.kt:281-291/319-370） ══
(function () {
  var btns = document.querySelectorAll('[data-role="resolve"]');
  if (!btns.length) return;
  btns.forEach(function (btn) {
    btn.addEventListener("click", function () {
      var approving = btn.dataset.approve === "1";
      window.confirmDialog({
        title: approving ? "处理举报" : "驳回举报", // 弹窗标题与正文同安卓（ReportsScreen.kt:322/326-327）
        body: approving
          ? "认定违规并处置（可在原帖菜单中删除内容）后，结果会通过社务箱通知举报人。附言选填。"
          : "驳回后举报结案，结果会通知举报人。附言选填。",
        reasonHint: "附言（选填）",
        reasonOptional: true, // 附言选填：空理由也放行（安卓同款，ReportsScreen.kt:326/327）
        reasonMax: 100,      // 安卓输入框 100 字上限（ReportsScreen.kt:334）
        confirm: approving ? "确认处理" : "确认驳回",
        onConfirm: function (note) {
          // 端点与请求体同安卓（ApiService.kt:290 / ApiDtos.kt:412）：approve/delete_target/reason。
          // 恒发 delete_target=false——删内容走帖子/绘马各自的菜单（安卓同款，ReportsScreen.kt:350）；
          // 层级校验（下不能管上）由服务端裁（misc_handlers.go:187-200），失败在弹层内联提示
          return window.api("POST", "/api/reports/" + btn.dataset.id + "/resolve", {
            approve: approving,
            delete_target: false,
            reason: note,
          });
        },
      }).then(function (ok) {
        if (!ok) return;
        toast(approving ? "举报已处理" : "举报已驳回"); // 文案同安卓 ShrineNoticeBar（ReportsScreen.kt:351）
        location.reload(); // 待处理队列是服务端渲染，重载＝安卓 load() 刷新列表
      });
    });
  });
})();

// ══ 他人主页：禁言 / 警告 / 调整身份（UserProfileScreen.kt:432-560, 875-1010）══
// 三张面板都照安卓的形制：禁言与警告是底部弹层，调整身份是居中对话框；
// 确认键在输入为空时保持禁用（安卓 enabled = text.isNotBlank() && !busy）。
function openMuteSheet(userID, nick) {
  var durations = [["1 小时", 1], ["1 天", 24], ["7 天", 168], ["30 天", 720], ["永久", 0]];
  var picked = 1; // 安卓默认 MuteDurations.first() = 1 小时
  var overlay = window.shrineSheet(
    '<div class="sheet-head"><span class="sheet-grip"></span><h3>禁言 ' + window.escHTML(nick) + '</h3></div>' +
    '<div class="section-label">选择禁言时长</div>' +
    '<div class="filter-pills" data-role="durs">' +
    durations.map(function (d, i) {
      return '<button class="filter-pill' + (i === 0 ? ' on' : '') + '" data-h="' + d[1] + '">' + d[0] + '</button>';
    }).join("") + '</div>' +
    '<div class="section-label">理由（必填）</div>' +
    '<div class="well mute-well"><textarea class="field" data-role="reason" maxlength="500" rows="2" placeholder="请说明禁言理由，会记入社务箱"></textarea></div>' +
    '<p class="form-error" data-role="err" hidden></p>' +
    '<button class="btn btn-full" data-role="ok" disabled>确认禁言</button>' +
    '<button class="btn-text" data-role="unmute">解除禁言</button>'
  );
  var sheet = overlay.querySelector(".sheet") || overlay;
  var okBtn = sheet.querySelector('[data-role="ok"]');
  var ta = sheet.querySelector('[data-role="reason"]');
  var err = sheet.querySelector('[data-role="err"]');
  function sync() { okBtn.disabled = !ta.value.trim(); }
  ta.addEventListener("input", sync);
  sheet.querySelectorAll("[data-h]").forEach(function (b) {
    b.addEventListener("click", function () {
      picked = +b.dataset.h;
      sheet.querySelectorAll("[data-h]").forEach(function (x) { x.classList.toggle("on", x === b); });
    });
  });
  okBtn.addEventListener("click", function () {
    var reason = ta.value.trim();
    if (!reason) { err.textContent = "禁言理由必填"; err.hidden = false; return; }
    okBtn.disabled = true;
    window.api("POST", "/api/users/" + userID + "/mute", { hours: picked, reason: reason })
      .then(function () { toast("已发送至对方社务箱"); location.reload(); })
      .catch(function (e) { okBtn.disabled = false; err.textContent = e.message; err.hidden = false; });
  });
  sheet.querySelector('[data-role="unmute"]').addEventListener("click", function () {
    window.api("POST", "/api/users/" + userID + "/unmute")
      .then(function () { toast("已解除禁言"); location.reload(); })
      .catch(function (e) { err.textContent = e.message; err.hidden = false; });
  });
}

function openWarnSheet(userID, nick) {
  var overlay = window.shrineSheet(
    '<div class="sheet-head"><span class="sheet-grip"></span><h3>警告 ' + window.escHTML(nick) + '</h3>' +
    '<p class="sheet-sub">警告内容会发送到对方的社务箱</p></div>' +
    '<div class="well warn-well"><textarea class="field" data-role="warn-text" maxlength="500" rows="3" placeholder="填写警告内容"></textarea></div>' +
    '<p class="form-error" data-role="err" hidden></p>' +
    '<button class="btn btn-full" data-role="ok" disabled>发送警告</button>'
  );
  var sheet = overlay.querySelector(".sheet") || overlay;
  var okBtn = sheet.querySelector('[data-role="ok"]');
  var ta = sheet.querySelector('[data-role="warn-text"]');
  var err = sheet.querySelector('[data-role="err"]');
  ta.addEventListener("input", function () { okBtn.disabled = !ta.value.trim(); });
  okBtn.addEventListener("click", function () {
    var text = ta.value.trim();
    if (!text) return;
    okBtn.disabled = true;
    window.api("POST", "/api/users/" + userID + "/warn", { content: text })
      .then(function () { toast("已发送至对方社务箱"); location.reload(); })
      .catch(function (e) { okBtn.disabled = false; err.textContent = e.message; err.hidden = false; });
  });
}

function openRoleSheet(userID, nick, current) {
  var roles = [["社员", "member"], ["临时奉仕", "temp_houshi"], ["奉仕", "houshi"],
    ["宫司", "guuji"], ["神主", "kannushi"]];
  var picked = current || "";
  var overlay = document.createElement("div");
  overlay.className = "dialog-overlay";
  overlay.innerHTML =
    '<div class="dialog neu-surface role-dialog">' +
    '<h3 class="dialog-title">调整身份</h3>' +
    '<p class="dialog-body">为 ' + window.escHTML(nick) + ' 选择新的身份，确认后立即生效，并通过社务箱通知对方。</p>' +
    '<div class="role-list">' +
    roles.map(function (r) {
      return '<button class="role-row' + (r[1] === picked ? ' on' : '') + '" data-r="' + r[1] + '">' +
        '<span class="role-dot"></span>' +
        '<span class="role-name">' + r[0] + '</span>' +
        (r[1] === current ? '<span class="role-cur">现任</span>' : '') +
        '</button>';
    }).join("") + '</div>' +
    '<p class="form-error" data-role="err" hidden></p>' +
    '<div class="dialog-actions">' +
    '<button class="btn-ghost" data-role="cancel">取消</button>' +
    '<button class="btn" data-role="ok" disabled>确认</button>' +
    '</div></div>';
  document.body.appendChild(overlay);
  var okBtn = overlay.querySelector('[data-role="ok"]');
  var err = overlay.querySelector('[data-role="err"]');
  function close() { overlay.remove(); }
  overlay.querySelectorAll(".role-row").forEach(function (b) {
    b.addEventListener("click", function () {
      picked = b.dataset.r;
      overlay.querySelectorAll(".role-row").forEach(function (x) {
        x.classList.toggle("on", x === b);
      });
      okBtn.disabled = picked === current;
    });
  });
  overlay.querySelector('[data-role="cancel"]').addEventListener("click", close);
  overlay.addEventListener("click", function (e) { if (e.target === overlay) close(); });
  okBtn.addEventListener("click", function () {
    if (!picked || picked === current) return;
    okBtn.disabled = true;
    window.api("POST", "/api/users/" + userID + "/role", { role: picked })
      .then(function () { toast("身份已调整"); location.reload(); })
      .catch(function (e) { okBtn.disabled = false; err.textContent = e.message; err.hidden = false; });
  });
}

// ══ 设置页：展示开关 / 通知范围 / 黑夜模式 ══// ══ 设置页：展示开关 / 通知范围 / 黑夜模式 ══
(function () {
  function patchMe(payload){ return window.api("PATCH", "/api/me", payload); }

  document.querySelectorAll('[data-role="toggle-field"]').forEach(function (btn) {
    btn.addEventListener("click", function () {
      var key = btn.dataset.key;
      var want = btn.dataset.on !== "1";
      var sw = btn.querySelector(".switch");
      sw.classList.toggle("on", want);          // 乐观
      btn.dataset.on = want ? "1" : "0";
      var fields = {}; fields[key] = want;
      patchMe({ show_fields: fields }).catch(function (e) {
        sw.classList.toggle("on", !want);        // 回滚
        btn.dataset.on = want ? "0" : "1";
        toast(e.message);
      });
    });
  });

  document.querySelectorAll('[data-role="toggle-scope"]').forEach(function (btn) {
    btn.addEventListener("click", function () {
      var key = btn.dataset.key;
      var want = btn.dataset.on !== "1";
      var sw = btn.querySelector(".switch");
      sw.classList.toggle("on", want);
      btn.dataset.on = want ? "1" : "0";
      var scope = {}; scope[key] = want;
      patchMe({ notify_scope: scope }).catch(function (e) {
        sw.classList.toggle("on", !want);
        btn.dataset.on = want ? "0" : "1";
        toast(e.message);
      });
    });
  });

  // 黑夜模式：切 <html class="dark">，存 localStorage
  var darkBtn = document.querySelector('[data-role="dark-mode"]');
  if (darkBtn) {
    var apply = function (on) {
      document.documentElement.classList.toggle("dark", on);
      var sw = darkBtn.querySelector(".switch");
      if (sw) sw.classList.toggle("on", on);
      darkBtn.dataset.on = on ? "1" : "0";
    };
    apply(localStorage.getItem("theme") === "dark");
    darkBtn.addEventListener("click", function () {
      var on = localStorage.getItem("theme") !== "dark";
      localStorage.setItem("theme", on ? "dark" : "light");
      apply(on);
    });
  }
})();

// ══ 神社祝部 · 绘马筛选弹层（ShrineBoardScreen.kt:575-625）══
// 顶栏筛选钮 → 底部弹层：状态四枚多选胶囊 + 时间排序两枚，底部「应用筛选」。
// 状态与排序拼进 URL 重载（服务端渲染，与分类页签同一套状态）。
(function () {
  var btn = document.querySelector('[data-role="ema-filter"]');
  if (!btn) return;
  // 胶囊文案 = 后端 wire 值本身（EmaState.label：Models.kt:236-241 就是
  // "open"/"in progress"/"resolved"/"closed"，与绘马卡上的状态 pill 同一套字）
  var states = [
    ["open", "open"], ["in_progress", "in progress"], ["resolved", "resolved"], ["closed", "closed"],
  ];
  var sorts = [["new", "最新"], ["old", "最旧"]];
  var cur = (new URLSearchParams(location.search).get("states") || "").split(",").filter(Boolean);
  var curSort = new URLSearchParams(location.search).get("sort") || "new";
  var picked = cur.slice();
  var sort = curSort;

  function render() {
    var chips = states.map(function (s) {
      var on = picked.indexOf(s[0]) >= 0;
      return '<button class="filter-pill st-' + s[0] + (on ? ' on' : '') + '" data-st="' + s[0] + '">' + s[1] + '</button>';
    }).join("");
    var sortChips = sorts.map(function (s) {
      return '<button class="filter-pill sort' + (s[0] === sort ? ' on' : '') + '" data-sort="' + s[0] + '">' + s[1] + '</button>';
    }).join("");
    return '<div class="sheet-head"><span class="sheet-grip"></span><h3>筛选绘马</h3>' +
      '<p class="sheet-sub">按状态筛选，或切换时间排序。</p></div>' +
      '<div class="section-label">状态</div><div class="filter-pills">' + chips + '</div>' +
      '<div class="section-label">时间排序</div><div class="filter-pills">' + sortChips + '</div>' +
      '<button class="btn btn-full" data-role="apply-filter">应用筛选</button>';
  }

  btn.addEventListener("click", function () {
    picked = cur.slice();
    sort = curSort;
    var overlay = window.shrineSheet(render());
    // 重绘必须落在 .sheet 里：shrineSheet 把内容包在 <div class="sheet"> 中，
    // 直接覆写 overlay.innerHTML 会把这层外壳连同底滑样式一起抹掉。
    var sheet = overlay.querySelector(".sheet") || overlay;
    function bind() {
      sheet.querySelectorAll('[data-st]').forEach(function (el) {
        el.addEventListener("click", function () {
          var k = el.dataset.st;
          var i = picked.indexOf(k);
          if (i >= 0) picked.splice(i, 1);
          else if (picked.length < 4) picked.push(k);
          sheet.innerHTML = render();
          bind();
        });
      });
      sheet.querySelectorAll('[data-sort]').forEach(function (el) {
        el.addEventListener("click", function () {
          sort = el.dataset.sort;
          sheet.innerHTML = render();
          bind();
        });
      });
      var apply = sheet.querySelector('[data-role="apply-filter"]');
      if (apply) apply.addEventListener("click", function () {
        var q = new URLSearchParams(location.search);
        q.set("tab", "ema");
        q.delete("page");
        if (picked.length) q.set("states", picked.join(",")); else q.delete("states");
        if (sort && sort !== "new") q.set("sort", sort); else q.delete("sort");
        location.href = "/shrine?" + q.toString();
      });
    }
    bind();
  });
})();

// ══ 通知详情：收藏通知开关（NoticeDetailScreen.kt「收藏通知」）══
(function () {
  var btn = document.querySelector('[data-role="star-notice"]');
  if (!btn) return;
  var busy = false;
  btn.addEventListener("click", function () {
    if (busy) return;
    busy = true;
    var before = btn.dataset.on === "1";
    var next = !before;
    btn.dataset.on = next ? "1" : "0";
    var sw = btn.querySelector(".switch");
    if (sw) sw.classList.toggle("on", next);
    window.api("POST", "/api/notices/" + btn.dataset.id + "/star", { starred: next })
      .then(function () { busy = false; })
      .catch(function (e) {
        btn.dataset.on = before ? "1" : "0";
        if (sw) sw.classList.toggle("on", before);
        busy = false;
        toast(e.message);
      });
  });
})();

// ══ 社务箱：全部已读 / 行内星标 / 行内已读切换 ══// ══ 社务箱：全部已读 / 行内星标 / 行内已读切换 ══
// 安卓：全部已读在列表右上（InboxScreen.kt:237），星标与已读是列表内的左右滑
// （:313-408）。网页用行尾两枚按钮表达同一组动作。
(function () {
  var allBtn = document.querySelector('[data-role="read-all"]');
  if (allBtn) allBtn.addEventListener("click", function () {
    if (allBtn.disabled) return;
    allBtn.disabled = true;
    window.api("POST", "/api/notices/read-all")
      .then(function () { toast("已全部标为已读"); location.reload(); })
      .catch(function (e) { allBtn.disabled = false; toast(e.message); });
  });

  document.querySelectorAll('[data-role="star"]').forEach(function (btn) {
    btn.addEventListener("click", function () {
      var next = btn.dataset.starred !== "1";
      btn.disabled = true;
      window.api("POST", "/api/notices/" + btn.dataset.id + "/star", { starred: next })
        .then(function () {
          btn.dataset.starred = next ? "1" : "0";
          btn.classList.toggle("on", next);
          // 收藏页里取消收藏后该行应消失，重载最省事（服务端分页由 URL 决定）
          if (document.querySelector('.pill-tabs a[href*="only=starred"].on')) location.reload();
          else btn.disabled = false;
        })
        .catch(function (e) { btn.disabled = false; toast(e.message); });
    });
  });

  // 删除通知（InboxScreen.kt:451-455 左滑删除 + :485-505 确认框防误删）
  document.querySelectorAll('[data-role="del-notice"]').forEach(function (btn) {
    btn.addEventListener("click", function (e) {
      e.preventDefault();
      e.stopPropagation();
      window.confirmDialog({
        title: "删除这条通知？",
        body: "删除后无法恢复。",
        confirm: "删除",
        danger: true,
        onConfirm: function () {
          return window.api("DELETE", "/api/notices/" + btn.dataset.id)
            .then(function () {
              var row = btn.closest(".inbox-row");
              if (row) row.remove();
              window.toast("已删除通知");
            });
        },
      });
    });
  });

  document.querySelectorAll('[data-role="toggle-read"]').forEach(function (btn) {
    btn.addEventListener("click", function () {
      var wasRead = btn.dataset.read === "1";
      btn.disabled = true;
      window.api("POST", "/api/notices/" + btn.dataset.id + (wasRead ? "/unread" : "/read"))
        .then(function () {
          btn.dataset.read = wasRead ? "0" : "1";
          btn.closest(".inbox-row").classList.toggle("unread", wasRead);
          btn.disabled = false;
        })
        .catch(function (e) { btn.disabled = false; toast(e.message); });
    });
  });
})();

// ══ 二级页返回钮：优先浏览器历史，直进时回主板块 ══
// 安卓 onBack = navController.popBackStack()（Scaffold.kt:88-98）；网页对应
// history.back()。但直链打开二级页时没有可退的历史，退回 /b/main。
(function () {
  var btn = document.querySelector('[data-role="back"]');
  if (!btn) return;
  btn.addEventListener("click", function (e) {
    if (history.length > 1 && document.referrer) {
      e.preventDefault();
      history.back();
    }
  });
})();

// ══ 设置页：阅读字号滑杆（ReadingScale：ReadingScale.kt:19-45 + SettingsScreen.kt:568-740）══
// 四档 0.9 / 1.0 / 1.15 / 1.3，仅作用于正文（app.css 的 --reading-scale 乘在正文块上）。
// 拖动跟手、松手吸附；落 localStorage，首帧由 <head> 里的 theme.js 应用，避免闪动。
(function () {
  var box = document.querySelector('[data-role="reading-slider"]');
  if (!box) return;
  var steps = [0.9, 1, 1.15, 1.3];
  var thumb = box.querySelector(".rs-thumb");
  var fill = box.querySelector(".rs-fill");
  var dots = box.querySelectorAll(".rs-dots i");
  var labels = box.querySelectorAll(".rs-labels span");
  var idx = 1;
  try {
    var saved = parseFloat(localStorage.getItem("readingScale"));
    if (saved) {
      var best = 0;
      steps.forEach(function (v, i) { if (Math.abs(v - saved) < Math.abs(steps[best] - saved)) best = i; });
      idx = best;
    }
  } catch (e) { /* 私密模式下 localStorage 不可用，按标准档 */ }

  function paint(i) {
    var pct = i / (steps.length - 1);
    thumb.style.left = "calc(14px + (100% - 28px) * " + pct + ")";
    fill.style.width = "calc((100% - 28px) * " + pct + ")";
    dots.forEach(function (d, k) { d.classList.toggle("on", k <= i); });
    labels.forEach(function (l, k) { l.classList.toggle("on", k === i); });
  }
  function apply(i) {
    idx = i;
    paint(i);
    document.documentElement.style.setProperty("--reading-scale", String(steps[i]));
    try { localStorage.setItem("readingScale", String(steps[i])); } catch (e) { /* 忽略 */ }
  }
  function posToIdx(clientX) {
    var r = box.getBoundingClientRect();
    var start = r.left + 14, end = r.right - 14;
    if (end <= start) return 0;
    return Math.round(((clientX - start) / (end - start)) * (steps.length - 1));
  }
  var dragging = false;
  function move(e) {
    if (!dragging) return;
    var x = e.touches ? e.touches[0].clientX : e.clientX;
    var i = Math.max(0, Math.min(steps.length - 1, posToIdx(x)));
    if (i !== idx) apply(i);
    e.preventDefault();
  }
  box.addEventListener("pointerdown", function (e) {
    dragging = true;
    apply(Math.max(0, Math.min(steps.length - 1, posToIdx(e.clientX))));
    box.setPointerCapture && box.setPointerCapture(e.pointerId);
  });
  box.addEventListener("pointermove", move);
  box.addEventListener("pointerup", function () { dragging = false; });
  box.addEventListener("pointercancel", function () { dragging = false; });
  apply(idx);
})();

// ══ 设置页：数据导出（SettingsScreen.kt「数据」组）══
// 安卓是 AlertDialog 里「生成并分享」，网页直接下载 Markdown 文件。
(function () {
  var btn = document.querySelector('[data-role="export-posts"]');
  if (!btn) return;
  btn.addEventListener("click", function () {
    window.confirmDialog({
      title: "数据导出",
      body: "将您的全部帖子生成为一个 Markdown 文件。",
      confirm: "生成并分享",
      onConfirm: function () {
        // 服务端 /api/me/export 直接回文件，开新窗口交给浏览器下载
        window.open("/api/me/export", "_blank");
        return Promise.resolve();
      },
    });
  });
})();

// ══ 设置页：黑夜模式开关首帧同步 ══// ══ 设置页：黑夜模式开关首帧同步 ══
// html.dark 是 theme.js 在 <head> 里按 localStorage 决定的，服务端不知道，
// 所以开关态以 DOM 为准回填，别让它显示成「关」。
(function () {
  var btn = document.querySelector('[data-role="dark-mode"]');
  if (!btn) return;
  var sw = btn.querySelector(".switch");
  if (sw) sw.classList.toggle("on", document.documentElement.classList.contains("dark"));
})();

// ══ 换栏目/换标签时保留滚动位置 ══
// 安卓的子板块切换（我的客房四 tab、祝部三子页、绘马筛选）都是原地重组：
// 滚动位置不动、只换下面那块内容（MyRoomScreen.kt:151、ShrineBoardScreen.kt:217）。
// 网页每次都是新文档，默认必然回顶。这里按「同页换参数」把 scrollY 存进
// sessionStorage，回来后立刻还原；真正的板块跳转（底栏四格）不还原。
(function () {
  var KEY = "scrollY:" + location.pathname;
  try {
    // 浏览器自带的滚动恢复会跟我们的还原打架，关掉
    if ("scrollRestoration" in history) history.scrollRestoration = "manual";
    var saved = sessionStorage.getItem(KEY);
    if (saved) {
      var y = parseInt(saved, 10) || 0;
      sessionStorage.removeItem(KEY);
      if (y > 0) {
        // 等首屏排版落定（字体/图片会改变高度）再跳，两次机会
        window.scrollTo(0, y);
        requestAnimationFrame(function () { window.scrollTo(0, y); });
        setTimeout(function () { window.scrollTo(0, y); }, 120);
      }
    }
  } catch (e) { /* 私密模式忽略 */ }

  // 只对「同页 query 变化」与显式标记的链接记账
  document.addEventListener("click", function (e) {
    var a = e.target.closest("a[href]");
    if (!a || a.target || e.metaKey || e.ctrlKey) return;
    var href = a.getAttribute("href");
    if (!href || href.charAt(0) === "#") return;
    var u;
    try { u = new URL(href, location.href); } catch (err) { return; }
    if (u.origin !== location.origin) return;
    // 同一路径（只换 query，如 ?tab=liked）或显式 data-keep-scroll
    var samePage = u.pathname === location.pathname;
    if (samePage || a.hasAttribute("data-keep-scroll")) {
      try { sessionStorage.setItem("scrollY:" + u.pathname, String(window.scrollY)); } catch (err) {}
    }
  }, true);
})();

// ══ 软键盘适配（对齐安卓的 imePadding）══
// 安卓给评论输入条、搜索框、撰写器、绘马回复、通知快捷回复都加了 imePadding()：
// 键盘弹出时这些输入区整体抬到键盘之上，不被遮住。
//
// 手机浏览器没有 imePadding，但有 visualViewport：
//   · viewport.height 缩小时说明键盘出现了
//   · 位移 = window.innerHeight - viewport.height - viewport.offsetTop
// 把「贴着底部的输入类元素」用 CSS 变量 --kb 顶起来（app.css 里消费它）。
//
// 桌面浏览器没有软键盘，visualViewport 不变，--kb 恒为 0，逻辑自然不生效。
(function () {
  var vv = window.visualViewport;
  if (!vv) return;

  var root = document.documentElement;
  var raf = 0;

  function apply() {
    raf = 0;
    // 键盘高度 = 布局视口高 - 可视视口高（Safari 在键盘弹出时还会整体上推，故要加 offsetTop）
    var kb = Math.max(0, Math.round(window.innerHeight - vv.height - vv.offsetTop));
    // 小于 80px 的差值多半是地址栏收起/展开，不当成键盘
    if (kb < 80) kb = 0;
    root.style.setProperty("--kb", kb + "px");
    root.classList.toggle("kb-open", kb > 0);
  }
  function schedule() {
    if (!raf) raf = requestAnimationFrame(apply);
  }

  vv.addEventListener("resize", schedule);
  vv.addEventListener("scroll", schedule);
  // 聚焦/失焦是键盘出现与收起的直接信号，补一次（有些浏览器 resize 晚一拍）
  document.addEventListener("focusin", function (e) {
    if (e.target && e.target.matches && e.target.matches("input,textarea")) {
      setTimeout(schedule, 120);
      setTimeout(schedule, 360);
    }
  });
  document.addEventListener("focusout", function () { setTimeout(schedule, 120); });

  apply();

  // 聚焦时把输入区滚进可视范围：键盘顶起后旧滚动位置会把光标行留在键盘后面
  document.addEventListener("focusin", function (e) {
    var el = e.target;
    if (!el || !el.matches || !el.matches("input,textarea")) return;
    setTimeout(function () {
      if (root.classList.contains("kb-open")) {
        el.scrollIntoView({ block: "center", behavior: "smooth" });
      }
    }, 380);
  });
})();

// ══ 时间文案（TimeFmt.kt:17-50 逐条同口径）══// ══ 时间文案（TimeFmt.kt:17-50 逐条同口径）══
// 毫秒入参（localStorage 的时间戳）；返回与安卓完全一致的中文相对时间。
window.webTimeText = function (ms) {
  if (!ms || ms <= 0) return "";
  var now = new Date();
  var t = new Date(ms);
  var pad = function (n) { return n < 10 ? "0" + n : "" + n; };
  var hm = pad(t.getHours()) + ":" + pad(t.getMinutes());
  var dayOf = function (d) { return Math.floor(new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime() / 86400000); };
  var today = dayOf(now), that = dayOf(t);
  var minutes = Math.floor((now - t) / 60000);
  if (that > today) return "刚刚";                       // 时钟偏慢落在未来
  if (minutes >= 0 && minutes <= 1) return "刚刚";
  if (minutes >= 2 && minutes <= 59) return minutes + " 分钟前";
  if (that === today && minutes >= 60 && minutes <= 119) return "1 小时前";
  if (that === today && minutes >= 120 && minutes <= 360) return Math.floor(minutes / 60) + " 小时前";
  if (that === today) return "今天 " + hm;
  if (that === today - 1) return "昨天 " + hm;
  if (that > today - 7) return (today - that) + " 天前";
  if (t.getFullYear() === now.getFullYear()) return (t.getMonth() + 1) + "月" + t.getDate() + "日";
  return t.getFullYear() + "年" + (t.getMonth() + 1) + "月" + t.getDate() + "日";
};

// ══ 搜索历史（SearchHistoryStore：最多 12 条、同词去重后置顶）══
// 安卓存进程内存（SearchScreen.kt:536），网页落 localStorage 刷新不丢。
(function () {
  var box = document.querySelector('[data-role="search-history"]');
  if (!box) return;
  var KEY = "arisu:searchHistory";
  var listEl = box.querySelector('[data-role="search-history-list"]');
  var clearBtn = box.querySelector('[data-role="search-history-clear"]');
  function read() {
    try {
      var v = JSON.parse(localStorage.getItem(KEY) || "[]");
      return Array.isArray(v) ? v : [];
    } catch (e) { return []; }
  }
  function write(list) {
    try { localStorage.setItem(KEY, JSON.stringify(list.slice(0, 12))); } catch (e) {}
  }
  function render() {
    var list = read();
    if (!list.length) {
      listEl.innerHTML = '<p class="sh-empty">还没有搜索记录</p>';
      return;
    }
    listEl.innerHTML = list.map(function (w) {
      return '<a class="sh-row" href="/search?q=' + encodeURIComponent(w) + '">' +
        '<svg class="ic" viewBox="0 0 24 24"><path d="M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z"/></svg>' +
        '<span>' + window.escHTML(w) + '</span></a>';
    }).join("");
  }
  render();
  if (clearBtn) clearBtn.addEventListener("click", function () {
    write([]);
    render();
  });

  // 记一条：搜索页提交时（submit / 回车 / 点历史项都会走 GET 导航）；
  // 用 form 提交事件捕获关键词，保证在导航前落盘
  var form = document.querySelector(".search-bar");
  if (form) form.addEventListener("submit", function () {
    var q = (form.querySelector('input[name="q"]') || {}).value || "";
    q = q.trim();
    if (!q) return;
    var list = read().filter(function (x) { return x !== q; });
    list.unshift(q);
    write(list);
  });
})();

// ══ 切换账号页（/me/accounts）：免密切换 / 移除 ══
// 与安卓 AccountManageScreen 同交互：点非当前账号即切换（服务端把该账号的
// 令牌装回 cookie），「移除」先确认再删档案。
(function () {
  var page = document.querySelector(".acct-row");
  if (!page) return;

  // 整行可点即切换（安卓 AccountManageScreen.kt:116-134：Row.clickable）；
  // 行尾「移除」是独立按钮，点它不能连带触发切换。
  document.querySelectorAll('[data-role="swap"].tappable').forEach(function (row) {
    row.addEventListener("click", function (e) {
      if (e.target.closest('[data-role="remove"]')) return;
      if (row.dataset.busy) return;
      row.dataset.busy = "1";
      window.api("POST", "/api/me/accounts/" + row.dataset.id + "/swap")
        .then(function (r) { location.href = (r && r.next) || "/me"; })
        .catch(function (err) { delete row.dataset.busy; window.toast(err.message); });
    });
  });

  document.querySelectorAll('[data-role="remove"]').forEach(function (btn) {
    btn.addEventListener("click", function () {
      window.confirmDialog({
        title: "移除这个账号？",
        body: "移除后这台设备不再保存它的登录状态，下次使用需要重新登录。",
        confirm: "移除",
        danger: true,
        onConfirm: function () {
          return window.api("DELETE", "/api/me/accounts/" + btn.dataset.id)
            .then(function () { location.reload(); });
        },
      });
    });
  });
})();

// ══ 通知详情：快捷回复（NoticeDetailScreen.kt:290-330）══
// 仅「回复」类通知且目标是帖子时存在；上限 2000 字，成功后提示「回复已发送」并收起。
(function () {
  var wrap = document.querySelector('[data-role="notice-reply"]');
  if (!wrap) return;
  var toggle = wrap.querySelector('[data-role="reply-toggle"]');
  var box = wrap.querySelector('[data-role="reply-box"]');
  var input = wrap.querySelector('[data-role="reply-input"]');
  var send = wrap.querySelector('[data-role="reply-send"]');
  var postNo = wrap.dataset.post;
  var sending = false;

  toggle.addEventListener("click", function () {
    var open = box.hidden;
    box.hidden = !open;
    toggle.textContent = open ? "收起回复框" : "快捷回复";
    if (open) input.focus();
  });

  function syncBtn() {
    send.disabled = !input.value.trim() || sending;
  }
  input.addEventListener("input", syncBtn);
  syncBtn();

  send.addEventListener("click", function () {
    var text = input.value.trim();
    if (!text || sending) return;
    sending = true;
    syncBtn();
    window.api("POST", "/api/posts/" + postNo + "/comments", { content: text })
      .then(function () {
        input.value = "";
        box.hidden = true;
        toggle.textContent = "快捷回复";
        window.toast("回复已发送");
      })
      .catch(function (e) { window.toast(e.message); })
      .then(function () { sending = false; syncBtn(); });
  });
})();

// ══ 草稿箱存储（对齐安卓 DraftStore：user+kind+target 唯一、最多 100 条、正文上限 40000）══
// 安卓是 filesDir/drafts.json 一个文件，网页对应 localStorage 的一个键；
// 写入走「先落临时对象再整体回写」，损坏时按空箱处理，不让草稿拖垮页面。
(function () {
  var KEY = "arisu:drafts";
  var MAX = 100, MAX_BODY = 40000;

  function readAll() {
    try {
      var list = JSON.parse(localStorage.getItem(KEY) || "[]");
      return Array.isArray(list) ? list : [];
    } catch (e) { return []; }
  }
  function writeAll(list) {
    try { localStorage.setItem(KEY, JSON.stringify(list.slice(0, MAX))); } catch (e) {}
  }
  function uid() {
    var el = document.querySelector('[data-role="last-account"]');
    return (el && el.dataset.email) || "anon";
  }

  // kind: comment_post | comment_ema | post | ema
  window.drafts = {
    list: function () {
      var u = uid();
      return readAll().filter(function (d) { return d.userId === u; })
        .sort(function (a, b) { return (b.updatedAt || 0) - (a.updatedAt || 0); });
    },
    get: function (kind, targetId) {
      var u = uid();
      return readAll().filter(function (d) {
        return d.userId === u && d.kind === kind && d.targetId === String(targetId);
      })[0] || null;
    },
    save: function (draft) {
      // updatedAt 语义同安卓（DraftStore.kt:92-96）：传了 0 才取当前时间
      var d = Object.assign({ userId: uid() }, draft);
      if (!d.updatedAt) d.updatedAt = Date.now();
      d.targetId = String(d.targetId || "new");
      d.body = String(d.body || "").slice(0, MAX_BODY);
      var list = readAll().filter(function (x) {
        return !(x.userId === d.userId && x.kind === d.kind && x.targetId === d.targetId);
      });
      list.push(d);
      list.sort(function (a, b) { return (b.updatedAt || 0) - (a.updatedAt || 0); });
      // 总量封顶 100 条（DraftStore.kt:102 take(MAX_DRAFTS)）——此前常量定义了却没用
      writeAll(list.slice(0, MAX));
    },
    remove: function (kind, targetId) {
      var u = uid();
      writeAll(readAll().filter(function (x) {
        return !(x.userId === u && x.kind === kind && x.targetId === String(targetId));
      }));
    },
  };
})();

// ══ 浏览足迹存储（对齐安卓 HistoryStore：同目标只留最近一次、上限 200）══
(function () {
  var KEY = "arisu:history";
  var MAX = 200;
  function readAll() {
    try {
      var list = JSON.parse(localStorage.getItem(KEY) || "[]");
      return Array.isArray(list) ? list : [];
    } catch (e) { return []; }
  }
  function writeAll(list) {
    try { localStorage.setItem(KEY, JSON.stringify(list.slice(0, MAX))); } catch (e) {}
  }
  function uid() {
    var el = document.querySelector('[data-role="last-account"]');
    return (el && el.dataset.email) || "anon";
  }
  window.historyStore = {
    list: function () {
      var u = uid();
      return readAll().filter(function (h) { return h.userId === u; })
        .sort(function (a, b) { return (b.visitedAt || 0) - (a.visitedAt || 0); });
    },
    get: function (kind, targetId) {
      var u = uid();
      return readAll().filter(function (h) {
        return h.userId === u && h.kind === kind && h.targetId === String(targetId);
      })[0] || null;
    },
    record: function (entry) {
      var userId = uid();
      var tid = String(entry.targetId);
      // 与已有那条合并：进详情页只带 kind/targetId/标题，滚动时补 lastCommentId，
      // 不合并的话后一次写会把前一次记下的续读位置冲掉
      var prev = readAll().filter(function (x) {
        return x.userId === userId && x.kind === entry.kind && x.targetId === tid;
      })[0] || {};
      var h = Object.assign({ userId: userId, visitedAt: Date.now() }, prev, entry);
      h.userId = userId;
      h.targetId = tid;
      var list = readAll().filter(function (x) {
        return !(x.userId === h.userId && x.kind === h.kind && x.targetId === h.targetId);
      });
      list.unshift(h);
      list.sort(function (a, b) { return (b.visitedAt || 0) - (a.visitedAt || 0); });
      // 上限 200 条（HistoryStore.kt:161 take(MAX_ENTRIES)）——此前常量定义了却没用
      writeAll(list.slice(0, MAX));
    },
    clear: function () {
      var u = uid();
      writeAll(readAll().filter(function (x) { return x.userId !== u; }));
    },
  };
})();

// ══ 帖子续读（PostDetailScreen.kt:355-380 + LastReadDivider:1394-1420）══
// 安卓在浏览足迹里记「最后读到的评论 id + 滚动偏移」，重进帖子时滚回那里，
// 并在那条评论上方画一条「上次读到」细线（朱色 α0.28 细线 + labelSmall 标签）。
// 网页对应：把最后一次滚动到的评论 id 记进 arisu:history，进页时还原位置。
(function () {
  var m = location.pathname.match(/^\/post\/(?:arisu_)?(\d+)$/);
  if (!m) return;
  var postId = m[1];
  var comments = [].slice.call(document.querySelectorAll(".comment[data-cid]"));
  if (!comments.length) return;

  var entry = window.historyStore.get("post", postId);
  var lastId = entry && entry.lastCommentId;

  // 还原：目标评论在 10 条之后才恢复（安卓同规则：target < 10 不滚动）
  if (lastId) {
    var idx = comments.findIndex(function (c) { return c.dataset.cid === String(lastId); });
    if (idx >= 10) {
      // 「上次读到」细线停在那条评论上方
      var div = document.createElement("div");
      div.className = "last-read-divider";
      div.innerHTML = '<span></span><b>上次读到</b><span></span>';
      comments[idx].parentNode.insertBefore(div, comments[idx]);
      // 滚到细线位置，并让锚点不贴顶
      requestAnimationFrame(function () {
        var top = div.getBoundingClientRect().top + window.scrollY - 72;
        window.scrollTo(0, Math.max(0, top));
      });
    }
  }

  // 记录：滚动停稳 400ms 后，把视口顶部那条评论记进足迹
  var timer = null;
  window.addEventListener("scroll", function () {
    clearTimeout(timer);
    timer = setTimeout(function () {
      var best = null;
      for (var i = 0; i < comments.length; i++) {
        var r = comments[i].getBoundingClientRect();
        if (r.bottom > 120) { best = comments[i]; break; }
      }
      if (!best) return;
      var cur = window.historyStore.get("post", postId) || {};
      window.historyStore.record({
        kind: "post", targetId: postId,
        title: cur.title || (document.querySelector(".post-title") || {}).textContent || postId,
        subtitle: cur.subtitle || "",
        lastCommentId: best.dataset.cid,
      });
    }, 400);
  }, { passive: true });
})();

// ══ 浏览足迹自动记录：帖子详情 / 绘马详情进页即记一条 ══
// 安卓在 PostDetailScreen / EmaDetailScreen 里 record（HistoryStore.record），
// 同一目标只留最近一次。标题取页面 h1，副标题取作者/板块。
(function () {
  var m = location.pathname.match(/^\/(post|ema)\/(.+)$/);
  if (!m) return;
  if (location.pathname.indexOf("/edit") > 0) return;
  var kind = m[1] === "post" ? "post" : "ema";
  var rawId = m[2].replace(/^arisu_/, "");
  // /post/new、/ema/new 不是内容页，记进足迹会得到 /post/arisu_new 这种死链
  if (!/^\d+$/.test(rawId)) return;
  // 标题优先取正文里的帖子/绘马标题（.page-head h1 是「帖子详情」这类页面名）
  var titleEl = document.querySelector(".post-title, .ema-title")
    || document.querySelector(".page-head h1");
  var subEl = document.querySelector(".page-head .sub, .post-detail .author-row .name");
  try {
    window.historyStore.record({
      kind: kind,
      targetId: rawId,
      title: (titleEl && titleEl.textContent.trim()) || rawId,
      subtitle: (subEl && subEl.textContent.trim()) || "",
    });
  } catch (e) { /* 私密模式忽略 */ }
})();

// ══ 草稿箱页（/me/drafts）：客户端渲染列表 ══
(function () {
  var page = document.querySelector('[data-role="drafts-page"]');
  if (!page) return;
  var pendingDelete = null;
  var modal = document.querySelector('[data-role="draft-del-modal"]');

  function kindLabel(kind) {
    if (kind === "comment_post" || kind === "comment_ema") return "回复草稿";
    if (kind === "post") return "发帖草稿";
    return "绘马草稿";
  }
  function titleOf(d) {
    if (d.kind === "comment_post" || d.kind === "comment_ema") {
      return "回复 · " + (d.targetTitle || d.targetId);
    }
    return d.title || (d.kind === "post" ? "未命名帖子" : "未命名绘马");
  }
  function hrefOf(d) {
    if (d.kind === "comment_post") return "/post/arisu_" + d.targetId + "#reply";
    if (d.kind === "comment_ema") return "/ema/" + d.targetId + "#reply";
    if (d.kind === "post") return "/post/new";
    return "/ema/new";
  }
  function esc(x) { return window.escHTML(x == null ? "" : x); }

  function render() {
    var list = window.drafts.list();
    var sub = document.querySelector(".page-head .sub");
    if (sub) sub.textContent = "共 " + list.length + " 条";
    if (!list.length) {
      // 空态用 ShrineEmptyState（DraftsScreen.kt:102-107）：标题「暂无草稿」+
      // 说明「回复、帖子与绘马的草稿会留在这里。」，与其它页同一个组件
      page.innerHTML = '<div class="empty-state">' +
        '<span class="es-art"></span>' +
        '<p class="es-title">暂无草稿</p>' +
        '<p class="es-note">回复、帖子与绘马的草稿会留在这里。</p>' +
        '</div>';
      return;
    }
    page.innerHTML = list.map(function (d) {
      var isReply = d.kind === "comment_post" || d.kind === "comment_ema";
      var icon = isReply
        ? '<svg class="ic" viewBox="0 0 24 24"><path d="M10 9V5l-7 7 7 7v-4.1c5 0 8.5 1.6 11 5.1-1-5-4-10-11-11z"/></svg>'
        : '<svg class="ic" viewBox="0 0 24 24"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34a.9959.9959 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>';
      return '<div class="draft-row">' +
        '<a class="dr-main" href="' + hrefOf(d) + '">' +
        '<span class="dr-ic">' + icon + '</span>' +
        '<span class="dr-body">' +
        '<span class="dr-top"><b>' + esc(titleOf(d)) + '</b><span class="pill">' + kindLabel(d.kind) + '</span></span>' +
        (d.body ? '<span class="dr-text">' + esc(d.body) + '</span>' : '') +
        '<span class="dr-time">' + window.webTimeText(d.updatedAt) + '</span>' +
        '</span></a>' +
        '<button class="dr-del" data-kind="' + d.kind + '" data-target="' + esc(d.targetId) + '" aria-label="删除草稿">' +
        '<svg class="ic" viewBox="0 0 24 24"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>' +
        '</button></div>';
    }).join("");

    page.querySelectorAll(".dr-del").forEach(function (btn) {
      btn.addEventListener("click", function () {
        pendingDelete = { kind: btn.dataset.kind, target: btn.dataset.target };
        if (modal) modal.hidden = false;
      });
    });
  }
  render();
  if (modal) {
    var cancel = modal.querySelector('[data-role="draft-del-cancel"]');
    var ok = modal.querySelector('[data-role="draft-del-ok"]');
    if (cancel) cancel.addEventListener("click", function () { modal.hidden = true; pendingDelete = null; });
    if (ok) ok.addEventListener("click", function () {
      if (pendingDelete) window.drafts.remove(pendingDelete.kind, pendingDelete.target);
      modal.hidden = true; pendingDelete = null; render();
    });
    modal.addEventListener("click", function (e) {
      if (e.target === modal) { modal.hidden = true; pendingDelete = null; }
    });
  }
})();

// ══ 浏览足迹页（/me/history）：按今天/昨天/更早分组 ══
(function () {
  var page = document.querySelector('[data-role="history-page"]');
  if (!page) return;
  var modal = document.querySelector('[data-role="history-clear-modal"]');
  function esc(x) { return window.escHTML(x == null ? "" : x); }
  function dayKey(ms) {
    var d = new Date(ms);
    return d.getFullYear() + "-" + (d.getMonth() + 1) + "-" + d.getDate();
  }
  function render() {
    var list = window.historyStore.list();
    var sub = document.querySelector(".page-head .sub");
    if (sub) sub.textContent = "共 " + list.length + " 条";
    // 顶部动作：有足迹才给「清除」
    var act = document.querySelector(".ph-actions");
    if (act && list.length && !act.querySelector('[data-role="history-clear"]')) {
      var b = document.createElement("button");
      b.className = "top-ic";
      b.setAttribute("data-role", "history-clear");
      b.setAttribute("aria-label", "清除足迹");
      b.innerHTML = '<svg class="ic" viewBox="0 0 24 24"><path d="M15 16h4v2h-4zm0-8h7v2h-7zm0 4h6v2h-6zM3 18c0 1.1.9 2 2 2h6c1.1 0 2-.9 2-2V8H3v10zM14 5h-3l-1-1H6L5 5H2v2h12z"/></svg>';
      b.addEventListener("click", function () { if (modal) modal.hidden = false; });
      act.appendChild(b);
    }
    if (!list.length) {
      // 空态用 ShrineEmptyState（HistoryScreen.kt:112-116）：标题「暂无足迹」+
      // 说明「看过的帖子与绘马会留在这里。」
      page.innerHTML = '<div class="empty-state">' +
        '<span class="es-art"></span>' +
        '<p class="es-title">暂无足迹</p>' +
        '<p class="es-note">看过的帖子与绘马会留在这里。</p>' +
        '</div>';
      return;
    }
    var today = dayKey(Date.now()), yesterday = dayKey(Date.now() - 86400000);
    var groups = { "今天": [], "昨天": [], "更早": [] };
    list.forEach(function (h) {
      var k = dayKey(h.visitedAt || 0);
      if (k === today) groups["今天"].push(h);
      else if (k === yesterday) groups["昨天"].push(h);
      else groups["更早"].push(h);
    });
    var html = "";
    ["今天", "昨天", "更早"].forEach(function (label) {
      var items = groups[label];
      if (!items.length) return;
      // 安卓给分组头显式加了 padding(h16, v8)（HistoryScreen.kt:122-125），
      // 覆盖 SectionLabel 自带的 top16/bottom10。
      html += '<div class="section-label hist-day">' + label + '</div>';
      items.forEach(function (h) {
        var isEma = h.kind === "ema";
        var href = isEma ? "/ema/" + h.targetId : "/post/arisu_" + h.targetId;
        var icon = isEma
          ? '<svg class="ic" viewBox="0 0 24 24"><path d="M12 2l2.4 7.4H22l-6.2 4.5 2.4 7.4L12 16.8 5.8 21.3l2.4-7.4L2 9.4h7.6z"/></svg>'
          : '<svg class="ic" viewBox="0 0 24 24"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM9 17H7v-7h2v7zm4 0h-2V7h2v10zm4 0h-2v-4h2v4z"/></svg>';
        html += '<a class="history-row" href="' + href + '">' +
          '<span class="h-ic">' + icon + '</span>' +
          '<span class="h-main"><b>' + esc(h.title || h.targetId) + '</b>' +
          '<span class="h-sub"><span>' + esc(h.subtitle) + '</span>' +
          '<span>' + window.webTimeText(h.visitedAt) + '</span></span></span></a>';
      });
    });
    page.innerHTML = html;
  }
  render();
  if (modal) {
    var cancel = modal.querySelector('[data-role="history-clear-cancel"]');
    var ok = modal.querySelector('[data-role="history-clear-ok"]');
    if (cancel) cancel.addEventListener("click", function () { modal.hidden = true; });
    if (ok) ok.addEventListener("click", function () {
      window.historyStore.clear();
      modal.hidden = true;
      var b = document.querySelector('[data-role="history-clear"]');
      if (b) b.remove();
      render();
    });
    modal.addEventListener("click", function (e) { if (e.target === modal) modal.hidden = true; });
  }
})();

// 列表滚动加载：只在茶话板（有 [data-infinite]）生效
bindInfiniteList();

// ══ 回到顶部阴阳玉（ScrollTopOrb.kt）══
// 安卓两种判定：列表页「首项下标>0 或 滚动偏移>300」，普通滚动页「>500px」。
// 网页只有 window 滚动，按「滚过半屏」近似（列表页一屏约 700px，取 500 与
// 安卓的 500px 阈值同量级）；点击平滑滚回顶部，时长与安卓同算法
// （0.7s 到顶、速度夹在 4000~42000 px/s，LinearOutSlowIn 收尾）。
(function () {
  var btn = document.querySelector('[data-role="scroll-top"]');
  if (!btn) return;
  var THRESHOLD = 500;                 // ScrollState.smoothScrollToTop 同款阈值
  var ticking = false;
  function sync() {
    btn.classList.toggle("on", window.scrollY > THRESHOLD);
    ticking = false;
  }
  window.addEventListener("scroll", function () {
    if (!ticking) { ticking = true; requestAnimationFrame(sync); }
  }, { passive: true });
  sync();

  btn.addEventListener("click", function () {
    var dist = window.scrollY;
    if (dist <= 0) return;
    // ScrollTopOrb.kt:130-137：dist/0.7 收到 4000..42000，时长 140..700ms
    var speed = Math.min(42000, Math.max(4000, dist / 0.7));
    var dur = Math.min(700, Math.max(140, Math.round(dist / speed * 1000)));
    var start = performance.now();
    var from = dist;
    function step(now) {
      var t = Math.min(1, (now - start) / dur);
      // LinearOutSlowIn 近似：ease-out 三次
      var e = 1 - Math.pow(1 - t, 3);
      window.scrollTo(0, Math.round(from * (1 - e)));
      if (t < 1) requestAnimationFrame(step);
    }
    requestAnimationFrame(step);
  });
})();

// ══ 最近登录账号写盘（layout 的 [data-role="last-account"]）══
// 原来靠 layout 里的内联脚本，CSP script-src 'self' 会拦掉；改走这里。
(function () {
  var el = document.querySelector('[data-role="last-account"]');
  if (!el) return;
  try {
    localStorage.setItem("lastEmail", el.dataset.email || "");
    localStorage.setItem("lastNick", el.dataset.nick || "");
    localStorage.setItem("lastAvatar", el.dataset.avatar || "");
  } catch (e) { /* 私密模式下不可写，忽略 */ }
})();

// ══ 二级页返回钮：优先浏览器历史，直进时回主板块 ══
// 安卓 onBack = navController.popBackStack()（Scaffold.kt:88-98）；网页对应
// history.back()。但直链打开二级页时没有可退的历史，退回 /b/main。
(function () {
  var btn = document.querySelector('[data-role="back"]');
  if (!btn) return;
  btn.addEventListener("click", function (e) {
    if (history.length > 1 && document.referrer) {
      e.preventDefault();
      history.back();
    }
  });
})();

// ══ 设置页：阅读字号滑杆（ReadingScale：ReadingScale.kt:19-45 + SettingsScreen.kt:568-740）══
// 四档 0.9 / 1.0 / 1.15 / 1.3，仅作用于正文（app.css 的 --reading-scale 乘在正文块上）。
// 拖动跟手、松手吸附；落 localStorage，首帧由 <head> 里的 theme.js 应用，避免闪动。
(function () {
  var box = document.querySelector('[data-role="reading-slider"]');
  if (!box) return;
  var steps = [0.9, 1, 1.15, 1.3];
  var thumb = box.querySelector(".rs-thumb");
  var fill = box.querySelector(".rs-fill");
  var dots = box.querySelectorAll(".rs-dots i");
  var labels = box.querySelectorAll(".rs-labels span");
  var idx = 1;
  try {
    var saved = parseFloat(localStorage.getItem("readingScale"));
    if (saved) {
      var best = 0;
      steps.forEach(function (v, i) { if (Math.abs(v - saved) < Math.abs(steps[best] - saved)) best = i; });
      idx = best;
    }
  } catch (e) { /* 私密模式下 localStorage 不可用，按标准档 */ }

  function paint(i) {
    var pct = i / (steps.length - 1);
    thumb.style.left = "calc(14px + (100% - 28px) * " + pct + ")";
    fill.style.width = "calc((100% - 28px) * " + pct + ")";
    dots.forEach(function (d, k) { d.classList.toggle("on", k <= i); });
    labels.forEach(function (l, k) { l.classList.toggle("on", k === i); });
  }
  function apply(i) {
    idx = i;
    paint(i);
    document.documentElement.style.setProperty("--reading-scale", String(steps[i]));
    try { localStorage.setItem("readingScale", String(steps[i])); } catch (e) { /* 忽略 */ }
  }
  function posToIdx(clientX) {
    var r = box.getBoundingClientRect();
    var start = r.left + 14, end = r.right - 14;
    if (end <= start) return 0;
    return Math.round(((clientX - start) / (end - start)) * (steps.length - 1));
  }
  var dragging = false;
  function move(e) {
    if (!dragging) return;
    var x = e.touches ? e.touches[0].clientX : e.clientX;
    var i = Math.max(0, Math.min(steps.length - 1, posToIdx(x)));
    if (i !== idx) apply(i);
    e.preventDefault();
  }
  box.addEventListener("pointerdown", function (e) {
    dragging = true;
    apply(Math.max(0, Math.min(steps.length - 1, posToIdx(e.clientX))));
    box.setPointerCapture && box.setPointerCapture(e.pointerId);
  });
  box.addEventListener("pointermove", move);
  box.addEventListener("pointerup", function () { dragging = false; });
  box.addEventListener("pointercancel", function () { dragging = false; });
  apply(idx);
})();

// ══ 设置页：黑夜模式开关首帧同步 ══
// html.dark 是 theme.js 在 <head> 里按 localStorage 决定的，服务端不知道，
// 所以开关态以 DOM 为准回填，别让它显示成「关」。
(function () {
  var btn = document.querySelector('[data-role="dark-mode"]');
  if (!btn) return;
  var sw = btn.querySelector(".switch");
  if (sw) sw.classList.toggle("on", document.documentElement.classList.contains("dark"));
})();

// ══ 最近登录账号写盘（layout 的 [data-role="last-account"]）══
// 原来靠 layout 里的内联脚本，CSP script-src 'self' 会拦掉；改走这里。
(function () {
  var el = document.querySelector('[data-role="last-account"]');
  if (!el) return;
  try {
    localStorage.setItem("lastEmail", el.dataset.email || "");
    localStorage.setItem("lastNick", el.dataset.nick || "");
    localStorage.setItem("lastAvatar", el.dataset.avatar || "");
  } catch (e) { /* 私密模式下不可写，忽略 */ }
})();

// ══ 绘马筛选面板（/shrine?tab=ema）：原生 <dialog> 开关 ══
(function () {
  var dlg = document.getElementById('ema-filter');
  if (!dlg) return;
  document.addEventListener('click', function (e) {
    var open = e.target.closest && e.target.closest('[data-open-filter]');
    if (open) { dlg.showModal(); return; }
    var close = e.target.closest && e.target.closest('[data-close-filter]');
    if (close) { dlg.close(); return; }
    // 点遮罩关闭：<dialog> 上点到的坐标在内容区之外
    if (e.target === dlg) {
      var r = dlg.getBoundingClientRect();
      if (e.clientY < r.top || e.clientY > r.bottom) dlg.close();
    }
  });
})();

// ══ 登录页「最近登录」：点一下把邮箱填进表单（AuthScreens.kt:328-339 的点击语义）══
(function () {
  var row = document.querySelector('.recent-row');
  if (!row) return;
  row.addEventListener('click', function (e) {
    var b = e.target.closest('[data-recent-email]');
    if (!b) return;
    var mail = document.querySelector('.auth-card input[name="email"]');
    if (!mail) return;
    mail.value = b.dataset.recentEmail;
    mail.dispatchEvent(new Event('input', { bubbles: true }));
    var pw = document.querySelector('.auth-card input[name="password"]');
    if (pw) pw.focus();
  });
})();

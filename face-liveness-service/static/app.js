// MOSIP Face Liveness & Biometric Verification Client

document.addEventListener("DOMContentLoaded", () => {
  // DOM Elements
  const video = document.getElementById("webcam");
  const overlay = document.getElementById("overlayCanvas");
  const ctx = overlay.getContext("2d");
  const fpsCounter = document.getElementById("fpsCounter");
  const ovalRing = document.getElementById("ovalRing");
  const guidanceBanner = document.getElementById("guidanceBanner");
  const guidanceText = document.getElementById("guidanceText");
  const guidanceIcon = document.getElementById("guidanceIcon");

  // Challenge Elements
  const challengeContainer = document.getElementById("challengeProgressContainer");
  const challengeTitle = document.getElementById("challengeTitle");
  const challengePercent = document.getElementById("challengePercent");
  const challengeProgressBar = document.getElementById("challengeProgressBar");
  const triggerChallengeBtn = document.getElementById("triggerChallengeBtn");

  // Score Elements
  const livenessScoreRing = document.getElementById("livenessScoreRing");
  const livenessScoreVal = document.getElementById("livenessScoreVal");
  const livenessStatusBadge = document.getElementById("livenessStatusBadge");

  const matchScoreRing = document.getElementById("matchScoreRing");
  const matchScoreVal = document.getElementById("matchScoreVal");
  const matchStatusBadge = document.getElementById("matchStatusBadge");

  const statusBadgeCard = document.getElementById("statusBadgeCard");
  const statusIconWrap = document.getElementById("statusIconWrap");
  const statusIcon = document.getElementById("statusIcon");
  const statusTitle = document.getElementById("statusTitle");
  const statusDesc = document.getElementById("statusDesc");

  const matchedUserCard = document.getElementById("matchedUserCard");
  const matchedUserName = document.getElementById("matchedUserName");
  const matchedUserId = document.getElementById("matchedUserId");
  const matchedUserAvatar = document.getElementById("matchedUserAvatar");

  const attackAlertCard = document.getElementById("attackAlertCard");
  const attackAlertMsg = document.getElementById("attackAlertMsg");

  // Diagnostics / Judge Mode
  const judgeModeToggle = document.getElementById("judgeModeToggle");
  const toggleTrack = document.getElementById("toggleTrack");
  const toggleThumb = document.getElementById("toggleThumb");
  const judgePanel = document.getElementById("judgePanel");
  const diagLapVar = document.getElementById("diagLapVar");
  const diagMotion = document.getElementById("diagMotion");
  const diagBlue = document.getElementById("diagBlue");
  const diagMarYaw = document.getElementById("diagMarYaw");
  const diagOnnx = document.getElementById("diagOnnx");
  const diagDeform = document.getElementById("diagDeform");

  // Tabs & Views
  const tabVerifyBtn = document.getElementById("tabVerifyBtn");
  const tabRegisterBtn = document.getElementById("tabRegisterBtn");
  const tabRegistryBtn = document.getElementById("tabRegistryBtn");
  const verifyPanel = document.getElementById("verifyPanel");
  const registerPanel = document.getElementById("registerPanel");
  const registryPanel = document.getElementById("registryPanel");
  const captureControlSection = document.getElementById("captureControlSection");
  const captureFaceBtn = document.getElementById("captureFaceBtn");
  const userCountBadge = document.getElementById("userCountBadge");
  const usersListContainer = document.getElementById("usersListContainer");
  const refreshUsersBtn = document.getElementById("refreshUsersBtn");

  // Registration Form
  const regUserId = document.getElementById("regUserId");
  const regName = document.getElementById("regName");
  const regRole = document.getElementById("regRole");
  const regFeedback = document.getElementById("regFeedback");

  // State Variables
  let currentTab = "verify"; // "verify" | "register" | "registry"
  let ws = null;
  let isStreaming = false;
  let triggerActiveChallengeNext = false;
  let isProcessing = false;
  let lastSendTime = 0;
  const TARGET_INTERVAL_MS = 60; // Smooth ~16 FPS, zero buffer backlog
  let frameCount = 0;
  let fpsTimer = performance.now();
  let lastCapturedFace = null;
  let displayedLiveScore = 0;
  let displayedMatchScore = 0;
  let noFaceCount = 0;
  let currentUiState = "";

  // Offscreen canvas for frame resizing
  const sendCanvas = document.createElement("canvas");
  const sendCtx = sendCanvas.getContext("2d");
  sendCanvas.width = 320;
  sendCanvas.height = 240;

  // --- 1. Camera Initialization ---
  async function initCamera() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { width: { ideal: 640 }, height: { ideal: 480 }, facingMode: "user" },
        audio: false
      });
      video.srcObject = stream;
      video.onloadedmetadata = () => {
        video.play();
        overlay.width = video.videoWidth || 640;
        overlay.height = video.videoHeight || 480;
        isStreaming = true;
        initWebSocket();
        requestAnimationFrame(streamLoop);
      };
    } catch (err) {
      console.error("Camera access error:", err);
      guidanceText.textContent = "Camera access denied. Please allow camera permissions.";
      guidanceIcon.className = "w-2 h-2 rounded-full bg-rose-500";
    }
  }

  // --- 2. WebSocket Connection ---
  function initWebSocket() {
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
      try { ws.close(); } catch(e) {}
    }
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const wsUrl = `${protocol}//${window.location.host}/ws/stream`;
    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      console.log("[WS] Connected to inference server");
      isProcessing = false;
    };

    ws.onmessage = (event) => {
      isProcessing = false;
      try {
        const data = JSON.parse(event.data);
        handleInferenceResponse(data);
      } catch (e) {
        console.error("WS Parse error", e);
      }
    };

    ws.onerror = () => {
      isProcessing = false;
    };

    ws.onclose = () => {
      isProcessing = false;
      console.warn("[WS] Disconnected, retrying in 1.5s...");
      setTimeout(initWebSocket, 1500);
    };
  }

  // --- 3. Stream Loop ---
  function streamLoop() {
    const now = performance.now();
    if (isStreaming && ws && ws.readyState === WebSocket.OPEN && video.readyState >= 2) {
      if (!isProcessing && (now - lastSendTime >= TARGET_INTERVAL_MS)) {
        isProcessing = true;
        lastSendTime = now;

        // Calculate FPS
        frameCount++;
        if (now - fpsTimer >= 1000) {
          fpsCounter.textContent = `${frameCount} FPS`;
          frameCount = 0;
          fpsTimer = now;
        }

        // Draw current video frame to downscaled sendCanvas
        sendCtx.drawImage(video, 0, 0, sendCanvas.width, sendCanvas.height);
        const b64 = sendCanvas.toDataURL("image/jpeg", 0.65);

        const payload = {
          image: b64,
          mode: currentTab,
          trigger_challenge: triggerActiveChallengeNext
        };

        triggerActiveChallengeNext = false;
        ws.send(JSON.stringify(payload));
      }
    }
    requestAnimationFrame(streamLoop);
  }

  // --- 4. Render Response & Biometrics ---
  function handleInferenceResponse(data) {
    // Clear overlay
    ctx.clearRect(0, 0, overlay.width, overlay.height);

    if (!data.face_detected) {
      noFaceCount++;
      if (noFaceCount < 15) {
        return;
      }
      ovalRing.className = "w-56 h-72 border-2 border-dashed border-slate-400/60 rounded-[50%] flex items-center justify-center shadow-lg transition-colors";
      guidanceText.textContent = "Position face inside the oval";
      guidanceIcon.className = "w-2 h-2 rounded-full bg-slate-400";
      challengeContainer.classList.add("hidden");
      currentUiState = "NO_FACE";
      resetScoreDisplays();
      return;
    }
    noFaceCount = 0;

    const scaleX = overlay.width / sendCanvas.width;
    const scaleY = overlay.height / sendCanvas.height;

    // Draw Face Landmarks on Canvas
    if (data.landmarks) {
      ctx.fillStyle = "rgba(59, 130, 246, 0.85)";
      ctx.shadowColor = "#3b82f6";
      ctx.shadowBlur = 6;
      for (const [key, pt] of Object.entries(data.landmarks)) {
        ctx.beginPath();
        ctx.arc(pt[0] * scaleX, pt[1] * scaleY, 4, 0, 2 * Math.PI);
        ctx.fill();
      }
      ctx.shadowBlur = 0;
    }

    // Guidance text
    guidanceText.textContent = data.guidance || "Checking liveness...";

    // Active Challenge Display
    if (data.active_challenge && !data.challenge_completed) {
      challengeContainer.classList.remove("hidden");
      challengeTitle.textContent = `Challenge: ${data.active_challenge.replace("_", " ")}`;
      challengePercent.textContent = `${Math.round(data.challenge_progress)}%`;
      challengeProgressBar.style.width = `${data.challenge_progress}%`;
      ovalRing.className = "w-56 h-72 border-2 rounded-[50%] flex items-center justify-center oval-active-challenge transition-colors";
    } else {
      challengeContainer.classList.add("hidden");
    }

    // PAD Attacks Handling
    if (data.attack_type && data.attack_type !== "NONE") {
      ovalRing.className = "w-56 h-72 border-2 rounded-[50%] flex items-center justify-center oval-attack transition-colors";
      if (data.attack_type === "PRINT_ATTACK") {
        attackAlertMsg.textContent = "Static Photo / Paper Print Attack detected. Verification blocked.";
      } else if (data.attack_type === "INVALID_SCALE") {
        attackAlertMsg.textContent = "Face scale too small (Handheld phone or distant subject). Move closer to fill oval.";
      } else {
        attackAlertMsg.textContent = "Digital Screen Replay / Video attack detected. Verification blocked.";
      }
      guidanceIcon.className = "w-2 h-2 rounded-full bg-rose-500 animate-ping";
    } else {
      attackAlertCard.classList.add("hidden");
    }

    // Update Liveness Ring Score with Deadband Stabilization
    const rawLive = Math.round(data.liveness_score);
    if (Math.abs(rawLive - displayedLiveScore) >= 2 || rawLive === 0 || rawLive >= 95 || (data.attack_type && data.attack_type !== "NONE")) {
      displayedLiveScore = rawLive;
    }
    livenessScoreVal.textContent = `${displayedLiveScore}%`;
    livenessScoreRing.setAttribute("stroke-dasharray", `${displayedLiveScore}, 100`);

    if (data.is_live) {
      livenessStatusBadge.textContent = "Live Person";
      livenessStatusBadge.className = "text-[11px] font-semibold px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200";
      livenessScoreRing.classList.remove("text-blue-600", "text-rose-500");
      livenessScoreRing.classList.add("text-emerald-600");
    } else if (data.attack_type !== "NONE") {
      livenessStatusBadge.textContent = "Spoof Detected";
      livenessStatusBadge.className = "text-[11px] font-semibold px-2 py-0.5 rounded-full bg-rose-50 text-rose-700 border border-rose-200";
      livenessScoreRing.classList.remove("text-blue-600", "text-emerald-600");
      livenessScoreRing.classList.add("text-rose-500");
    } else {
      livenessStatusBadge.textContent = "Evaluating...";
      livenessStatusBadge.className = "text-[11px] font-semibold px-2 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200";
    }

    // Update Biometric Match Score with Deadband Stabilization
    const rawMatch = Math.round(data.match_score);
    if (Math.abs(rawMatch - displayedMatchScore) >= 2 || rawMatch === 0 || rawMatch >= 75) {
      displayedMatchScore = rawMatch;
    }
    matchScoreVal.textContent = `${displayedMatchScore}%`;
    matchScoreRing.setAttribute("stroke-dasharray", `${displayedMatchScore}, 100`);

    if (data.matched_user) {
      matchedUserCard.classList.remove("hidden");
      matchedUserName.textContent = data.matched_user.name;
      matchedUserId.textContent = `ID: ${data.matched_user.user_id} • ${data.matched_user.role || 'Resident'}`;
      matchStatusBadge.textContent = "Matched";
      matchStatusBadge.className = "text-[11px] font-semibold px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200";
    } else {
      matchedUserCard.classList.add("hidden");
      matchStatusBadge.textContent = "No Match (<75%)";
      matchStatusBadge.className = "text-[11px] font-semibold px-2 py-0.5 rounded-full bg-slate-100 text-slate-600";
    }

    // Verification Master Status Card (State-cached to stop flickering)
    const targetState = data.verified
      ? `VERIFIED_${data.matched_user?.user_id}`
      : (data.attack_type && data.attack_type !== "NONE"
          ? `ATTACK_${data.attack_type}`
          : (data.is_live ? "LIVE" : "EVAL"));

    if (targetState !== currentUiState) {
      currentUiState = targetState;
      if (data.verified) {
        ovalRing.className = "w-56 h-72 border-2 rounded-[50%] flex items-center justify-center oval-verified transition-colors";
        statusBadgeCard.className = "bg-white rounded-2xl p-5 border border-emerald-300 shadow-md text-center bg-gradient-to-b from-white to-emerald-50/50 transition-all";
        statusIconWrap.className = "w-16 h-16 rounded-full mx-auto flex items-center justify-center mb-3 bg-emerald-100 text-emerald-600 shadow-inner";
        statusTitle.textContent = "VERIFIED: ACCESS GRANTED";
        statusTitle.className = "text-xl font-extrabold text-emerald-800 tracking-tight";
        statusDesc.textContent = `Identity authenticated as ${data.matched_user?.name} with verified liveness.`;
        guidanceIcon.className = "w-2 h-2 rounded-full bg-emerald-500";
      } else if (data.attack_type && data.attack_type !== "NONE") {
        statusBadgeCard.className = "bg-white rounded-2xl p-5 border border-rose-300 shadow-md text-center bg-gradient-to-b from-white to-rose-50/50 transition-all";
        statusIconWrap.className = "w-16 h-16 rounded-full mx-auto flex items-center justify-center mb-3 bg-rose-100 text-rose-600 shadow-inner";
        statusTitle.textContent = "ACCESS DENIED";
        statusTitle.className = "text-xl font-extrabold text-rose-800 tracking-tight";
        statusDesc.textContent = "Presentation Attack Detection failed. Verification aborted.";
        guidanceIcon.className = "w-2 h-2 rounded-full bg-rose-500";
      } else {
        statusBadgeCard.className = "bg-white rounded-2xl p-5 border border-slate-200 shadow-sm text-center transition-all";
        statusIconWrap.className = "w-16 h-16 rounded-full mx-auto flex items-center justify-center mb-3 bg-slate-100 text-slate-400";
        statusTitle.textContent = "Evaluating Face Stream";
        statusTitle.className = "text-xl font-extrabold text-slate-800 tracking-tight";
        statusDesc.textContent = data.guidance || "Keep face centered in view.";
        guidanceIcon.className = "w-2 h-2 rounded-full bg-blue-500";
      }
    } else {
      if (data.guidance && statusDesc.textContent !== data.guidance && !data.verified && (!data.attack_type || data.attack_type === "NONE")) {
        statusDesc.textContent = data.guidance;
      }
    }

    // Diagnostics / Judge HUD Telemetry
    if (data.diagnostics) {
      if (diagOnnx && data.diagnostics.onnx_class) {
        diagOnnx.textContent = `${data.diagnostics.onnx_class} (${data.diagnostics.onnx_live_prob}%)`;
      }
      if (diagDeform && data.diagnostics.internal_deformation !== undefined) {
        diagDeform.textContent = data.diagnostics.internal_deformation.toFixed(4);
      }
      diagLapVar.textContent = data.diagnostics.laplacian_variance ?? "0.0";
      diagMotion.textContent = data.diagnostics.micro_motion ?? "0.000";
      diagBlue.textContent = data.diagnostics.blue_dominance ?? "0.00";
      diagMarYaw.textContent = `${data.diagnostics.mouth_aspect_ratio ?? "0.0"} / ${data.diagnostics.head_yaw_ratio ?? "0.0"}`;
    }
  }

  function resetScoreDisplays() {
    displayedLiveScore = 0;
    displayedMatchScore = 0;
    livenessScoreVal.textContent = "0%";
    livenessScoreRing.setAttribute("stroke-dasharray", "0, 100");
    livenessStatusBadge.textContent = "Pending";
    livenessStatusBadge.className = "text-[11px] font-semibold px-2 py-0.5 rounded-full bg-slate-100 text-slate-600";

    matchScoreVal.textContent = "0%";
    matchScoreRing.setAttribute("stroke-dasharray", "0, 100");
    matchStatusBadge.textContent = "Threshold: 75%";

    matchedUserCard.classList.add("hidden");
    attackAlertCard.classList.add("hidden");

    statusBadgeCard.className = "bg-white rounded-2xl p-5 border border-slate-200 shadow-sm text-center";
    statusIconWrap.className = "w-16 h-16 rounded-full mx-auto flex items-center justify-center mb-3 bg-slate-100 text-slate-400";
    statusTitle.textContent = "Awaiting Face Scan";
    statusTitle.className = "text-xl font-extrabold text-slate-800 tracking-tight";
    statusDesc.textContent = "Look directly at the camera to start liveness assessment";
  }

  // --- 5. Active Challenge Button ---
  triggerChallengeBtn.addEventListener("click", () => {
    triggerActiveChallengeNext = true;
  });

  // --- 6. Judge Mode Toggle ---
  judgeModeToggle.addEventListener("change", (e) => {
    if (e.target.checked) {
      judgePanel.classList.remove("hidden");
      toggleTrack.classList.replace("bg-slate-300", "bg-blue-600");
      toggleThumb.classList.replace("left-0.5", "left-4");
    } else {
      judgePanel.classList.add("hidden");
      toggleTrack.classList.replace("bg-blue-600", "bg-slate-300");
      toggleThumb.classList.replace("left-4", "left-0.5");
    }
  });

  // --- 7. Tab Switching ---
  tabVerifyBtn.addEventListener("click", () => switchTab("verify"));
  tabRegisterBtn.addEventListener("click", () => switchTab("register"));
  tabRegistryBtn.addEventListener("click", () => switchTab("registry"));

  function switchTab(tab) {
    currentTab = tab;
    [tabVerifyBtn, tabRegisterBtn, tabRegistryBtn].forEach(btn => {
      btn.className = "tab-btn inactive-tab flex items-center space-x-2 px-5 py-2.5 rounded-xl font-semibold text-sm transition";
    });

    verifyPanel.classList.add("hidden");
    registerPanel.classList.add("hidden");
    registryPanel.classList.add("hidden");
    captureControlSection.classList.add("hidden");

    if (tab === "verify") {
      tabVerifyBtn.className = "tab-btn active-tab flex items-center space-x-2 px-5 py-2.5 rounded-xl font-semibold text-sm transition";
      verifyPanel.classList.remove("hidden");
    } else if (tab === "register") {
      tabRegisterBtn.className = "tab-btn active-tab flex items-center space-x-2 px-5 py-2.5 rounded-xl font-semibold text-sm transition";
      registerPanel.classList.remove("hidden");
      captureControlSection.classList.remove("hidden");
    } else if (tab === "registry") {
      tabRegistryBtn.className = "tab-btn active-tab flex items-center space-x-2 px-5 py-2.5 rounded-xl font-semibold text-sm transition";
      registryPanel.classList.remove("hidden");
      fetchUsersList();
    }
  }

  // --- 8. Resident Face Capture & Registration ---
  captureFaceBtn.addEventListener("click", async () => {
    const uid = regUserId.value.trim();
    const name = regName.value.trim();
    const role = regRole.value;

    if (!uid || !name) {
      showRegFeedback("Please enter both Resident ID and Full Legal Name.", "error");
      return;
    }

    // Capture full resolution frame from video
    const snapCanvas = document.createElement("canvas");
    snapCanvas.width = video.videoWidth || 640;
    snapCanvas.height = video.videoHeight || 480;
    const snapCtx = snapCanvas.getContext("2d");
    snapCtx.drawImage(video, 0, 0, snapCanvas.width, snapCanvas.height);
    const snapB64 = snapCanvas.toDataURL("image/jpeg", 0.90);

    captureFaceBtn.disabled = true;
    captureFaceBtn.innerHTML = "<span>Analyzing & Registering...</span>";

    try {
      const res = await fetch("/api/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          user_id: uid,
          name: name,
          role: role,
          image_b64: snapB64
        })
      });

      const resData = await res.json();
      if (res.ok) {
        showRegFeedback(`Registration successful! Face enrolled with ${resData.liveness_score}% liveness confidence.`, "success");
        regUserId.value = "";
        regName.value = "";
        fetchUsersList();
        setTimeout(() => switchTab("verify"), 1500);
      } else {
        showRegFeedback(resData.detail || "Registration failed. Ensure face is live and clearly visible.", "error");
      }
    } catch (err) {
      showRegFeedback("Network error connecting to registration server.", "error");
    } finally {
      captureFaceBtn.disabled = false;
      captureFaceBtn.innerHTML = `
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z"/>
        </svg>
        <span>Capture & Register Face</span>
      `;
    }
  });

  function showRegFeedback(msg, type) {
    regFeedback.classList.remove("hidden");
    if (type === "success") {
      regFeedback.className = "text-xs font-semibold p-3 rounded-xl bg-emerald-50 text-emerald-800 border border-emerald-200";
    } else {
      regFeedback.className = "text-xs font-semibold p-3 rounded-xl bg-rose-50 text-rose-800 border border-rose-200";
    }
    regFeedback.textContent = msg;
  }

  // --- 9. Users Registry Fetching & Listing ---
  async function fetchUsersList() {
    try {
      const res = await fetch("/api/users");
      const users = await res.json();
      userCountBadge.textContent = users.length;

      if (!users || users.length === 0) {
        usersListContainer.innerHTML = '<p class="text-xs text-slate-400 text-center py-8">No registered profiles yet. Use "Register Resident Face" to add one.</p>';
        return;
      }

      usersListContainer.innerHTML = "";
      users.forEach(u => {
        const item = document.createElement("div");
        item.className = "flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-200 hover:bg-white transition";
        item.innerHTML = `
          <div class="flex items-center space-x-3">
            <div class="w-10 h-10 rounded-lg bg-slate-200 border border-slate-300 overflow-hidden flex items-center justify-center font-bold text-slate-600">
              ${u.thumbnail ? `<img src="${u.thumbnail}" class="w-full h-full object-cover">` : u.name[0]}
            </div>
            <div>
              <p class="text-xs font-bold text-slate-800">${u.name}</p>
              <p class="text-[11px] font-mono text-slate-500">ID: ${u.user_id} • ${u.role}</p>
            </div>
          </div>
          <button class="delete-user-btn text-xs text-rose-600 hover:text-rose-800 font-semibold px-2 py-1 rounded" data-id="${u.user_id}">
            Delete
          </button>
        `;
        usersListContainer.appendChild(item);
      });

      // Attach delete handlers
      document.querySelectorAll(".delete-user-btn").forEach(btn => {
        btn.addEventListener("click", async (e) => {
          const uid = e.target.getAttribute("data-id");
          if (confirm(`Delete registered user profile ${uid}?`)) {
            await fetch(`/api/users/${uid}`, { method: "DELETE" });
            fetchUsersList();
          }
        });
      });
    } catch (err) {
      console.error("Error fetching users:", err);
    }
  }

  refreshUsersBtn.addEventListener("click", fetchUsersList);

  // Restart camera button
  document.getElementById("cameraToggleBtn").addEventListener("click", initCamera);

  // Initialize on load
  initCamera();
  fetchUsersList();
});

const endpoints = {
	matches: "/api/linkedin/jobs/matched",
	profile: "/api/linkedin/resume/profile",
	sendBulk: "/api/emails/send",
	sendShortlisted: "/api/emails/send-shortlisted",
	bulkDraft: "/api/emails/bulk-draft",
	resumePreview: "/api/emails/bulk-draft/resume",
	profileSettings: "/api/profile/settings",
	resumeUpload: "/api/profile/resume",
	sentHistory: "/api/profile/sent-history"
};

const state = {
	jobs: [],
	matches: [],
	activeFilter: "all",
	bulkDraft: null,
	bulkTab: "recipients"
};

const elements = {
	status: document.querySelector("#system-status"),
	pulse: document.querySelector("#system-status .pulse"),
	profileState: document.querySelector("#profile-state"),
	profileCard: document.querySelector("#profile-card"),
	profileEmail: document.querySelector("#profile-email"),
	profilePassword: document.querySelector("#profile-password"),
	profilePasswordMask: document.querySelector("#profile-password-mask"),
	profileResumeSummary: document.querySelector("#profile-resume-summary"),
	resumeUpload: document.querySelector("#resume-upload"),
	sentHistoryList: document.querySelector("#sent-history-list"),
	jobsState: document.querySelector("#jobs-state"),
	jobsList: document.querySelector("#jobs-list"),
	activityLog: document.querySelector("#activity-log"),
	processConsole: document.querySelector("#process-console"),
	consoleState: document.querySelector("#console-state"),
	metricJobs: document.querySelector("#metric-jobs"),
	metricMatches: document.querySelector("#metric-matches"),
	metricAverage: document.querySelector("#metric-average"),
	filterButtons: [...document.querySelectorAll("[data-filter]")],
	actionButtons: [...document.querySelectorAll("button")],
	bulkModal: document.querySelector("#bulk-modal"),
	bulkMessage: document.querySelector("#bulk-modal-message"),
	recipientTable: document.querySelector("#recipient-table"),
	bulkSubject: document.querySelector("#bulk-subject"),
	bulkBody: document.querySelector("#bulk-body"),
	resumeSummary: document.querySelector("#resume-summary"),
	resumePreview: document.querySelector("#resume-preview"),
	bulkTabs: [...document.querySelectorAll("[data-bulk-tab]")],
	bulkPanels: [...document.querySelectorAll("[data-bulk-panel]")]
};

document.querySelector("#load-profile-btn").addEventListener("click", loadProfile);
document.querySelector("#save-profile-settings-btn").addEventListener("click", saveProfileSettings);
document.querySelector("#upload-resume-btn").addEventListener("click", uploadResume);
document.querySelector("#refresh-sent-history-btn").addEventListener("click", loadSentHistory);
document.querySelector("#load-matches-btn").addEventListener("click", loadMatches);
document.querySelector("#send-bulk-btn").addEventListener("click", openBulkModal);
document.querySelector("#send-shortlisted-btn").addEventListener("click", sendShortlistedJobs);
document.querySelector("#close-bulk-modal-btn").addEventListener("click", closeBulkModal);
document.querySelector("#add-recipient-btn").addEventListener("click", () => addRecipientRow());
document.querySelector("#save-bulk-draft-btn").addEventListener("click", saveBulkDraft);
document.querySelector("#send-bulk-confirm-btn").addEventListener("click", sendBulkFromModal);

elements.bulkModal.addEventListener("click", (event) => {
	if (event.target === elements.bulkModal) {
		closeBulkModal();
	}
});

document.addEventListener("keydown", (event) => {
	if (event.key === "Escape" && !elements.bulkModal.classList.contains("hidden")) {
		closeBulkModal();
	}
});

elements.filterButtons.forEach((button) => {
	button.addEventListener("click", () => {
		state.activeFilter = button.dataset.filter;
		elements.filterButtons.forEach((item) => item.classList.toggle("active", item === button));
		renderMatches();
	});
});

elements.bulkTabs.forEach((button) => {
	button.addEventListener("click", () => showBulkTab(button.dataset.bulkTab));
});

initAnimatedBackground();
loadProfile();

function initAnimatedBackground() {
	const target = document.querySelector("#animated-bg");
	const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

	if (!target || prefersReducedMotion) {
		return;
	}

	if (!window.VANTA || !window.VANTA.GLOBE || !window.THREE) {
		target.classList.add("fallback-active");
		return;
	}

	const effect = window.VANTA.GLOBE({
		el: target,
		THREE: window.THREE,
		mouseControls: true,
		touchControls: true,
		gyroControls: false,
		minHeight: 200,
		minWidth: 200,
		scale: 1,
		scaleMobile: 1,
		color: 0x0b766f,
		color2: 0x12b76a,
		backgroundColor: 0xf1f6f8,
		size: 0.9
	});

	window.addEventListener("beforeunload", () => effect.destroy());
}

async function loadProfile() {
	await withBusy("Loading profile", async () => {
		elements.profileState.textContent = "Loading profile settings and reading resume profile...";
		const [settings, profile] = await Promise.all([
			requestJson(endpoints.profileSettings),
			requestJson(endpoints.profile)
		]);
		renderProfileSettings(settings);
		renderProfile(profile);
		await loadSentHistory();
		logActivity(`Loaded profile for ${profile.name || "candidate"}.`);
	});
}

async function saveProfileSettings() {
	await withBusy("Saving profile settings", async () => {
		const settings = await requestJson(endpoints.profileSettings, {
			method: "PUT",
			headers: {
				Accept: "application/json",
				"Content-Type": "application/json"
			},
			body: JSON.stringify({
				email: elements.profileEmail.value.trim(),
				password: elements.profilePassword.value
			})
		});
		elements.profilePassword.value = "";
		renderProfileSettings(settings);
		logActivity("Saved Gmail profile settings.");
	});
}

async function uploadResume() {
	const file = elements.resumeUpload.files[0];
	if (!file) {
		logActivity("Choose a resume PDF before uploading.");
		return;
	}

	await withBusy("Uploading resume", async () => {
		const formData = new FormData();
		formData.append("resume", file);
		const settings = await requestJson(endpoints.resumeUpload, {
			method: "POST",
			body: formData
		});
		elements.resumeUpload.value = "";
		renderProfileSettings(settings);
		logActivity(`Uploaded resume ${settings.resume?.fileName || file.name}.`);
	});
}

async function loadMatches() {
	await withBusy("Finding matches", async () => {
		const progress = startProcessConsole("Finding matches", [
			"Loading your parsed resume profile for comparison.",
			"Opening LinkedIn and collecting recent posts from the feed.",
			"Extracting job title, recruiter, skills, location, and contact emails.",
			"Checking recent sent-email history to avoid repeated outreach.",
			"Filtering jobs by your target experience range.",
			"Comparing required skills with your resume skills.",
			"Scoring each job and keeping strong matches.",
			"Generating email subject and body drafts for shortlisted jobs."
		]);
		elements.jobsState.classList.remove("hidden");
		elements.jobsState.innerHTML = "<strong>Matching jobs...</strong><span>Scraping posts and comparing them with your resume profile.</span>";
		try {
			state.matches = withMatchIds(await requestJson(endpoints.matches));
			state.jobs = state.matches.map((match) => match.job).filter(Boolean);
			renderMetrics();
			renderMatches();
			addConsoleLine(`Shortlist ready with ${state.matches.length} matched opportunit${state.matches.length === 1 ? "y" : "ies"}.`, "success");
			logActivity(`Found ${state.matches.length} matched opportunit${state.matches.length === 1 ? "y" : "ies"}.`);
		} finally {
			stopProcessConsole(progress, "Done");
		}
	});
}

async function openBulkModal() {
	elements.bulkModal.classList.remove("hidden");
	setBulkMessage("Loading bulk email details...");
	showBulkTab("recipients");

	await withBusy("Loading bulk draft", async () => {
		state.bulkDraft = await requestJson(endpoints.bulkDraft);
		renderBulkDraft(state.bulkDraft);
		setBulkMessage("Review recipients, subject, body, and attached resume before sending.");
	});
}

function closeBulkModal() {
	elements.bulkModal.classList.add("hidden");
}

function showBulkTab(tabName) {
	state.bulkTab = tabName;
	elements.bulkTabs.forEach((button) => {
		button.classList.toggle("active", button.dataset.bulkTab === tabName);
	});
	elements.bulkPanels.forEach((panel) => {
		panel.classList.toggle("hidden", panel.dataset.bulkPanel !== tabName);
	});
}

function renderBulkDraft(draft) {
	elements.recipientTable.innerHTML = `
		<div class="recipient-header">
			<span>Email</span>
			<span>Name</span>
			<span></span>
		</div>
	`;

	(draft.recipients || []).forEach((recipient) => addRecipientRow(recipient.email, recipient.name));
	if (!draft.recipients || draft.recipients.length === 0) {
		addRecipientRow();
	}

	elements.bulkSubject.value = draft.subject || "";
	elements.bulkBody.value = draft.bodyTemplate || "";
	renderResume(draft.resume);
}

function addRecipientRow(email = "", name = "") {
	const row = document.createElement("div");
	row.className = "recipient-row";
	row.innerHTML = `
		<input class="recipient-input" type="email" placeholder="recruiter@company.com" value="${escapeAttribute(email)}" aria-label="Recruiter email">
		<input class="recipient-input" type="text" placeholder="Recruiter name" value="${escapeAttribute(name)}" aria-label="Recruiter name">
		<button class="remove-recipient" type="button" aria-label="Remove recipient" title="Remove recipient">×</button>
	`;
	row.querySelector(".remove-recipient").addEventListener("click", () => row.remove());
	elements.recipientTable.appendChild(row);
}

function renderResume(resume) {
	const safeResume = resume || {};
	const size = safeResume.sizeBytes ? formatBytes(safeResume.sizeBytes) : "Unknown size";
	const available = Boolean(safeResume.available);

	elements.resumeSummary.innerHTML = `
		<strong>${escapeHtml(safeResume.fileName || "No resume configured")}</strong>
		<span>${escapeHtml(safeResume.message || "Resume details are unavailable.")}</span>
		<span class="muted">${escapeHtml(safeResume.path || "No path")} ${available ? `• ${size}` : ""}</span>
	`;

	elements.resumePreview.classList.toggle("hidden", !available);
	if (available) {
		elements.resumePreview.src = `${endpoints.resumePreview}?t=${Date.now()}`;
	} else {
		elements.resumePreview.removeAttribute("src");
	}
}

function renderProfileSettings(settings) {
	const safeSettings = settings || {};
	elements.profileEmail.value = safeSettings.email || "";
	elements.profilePasswordMask.textContent = safeSettings.passwordConfigured
		? `Current: ${safeSettings.maskedPassword}`
		: "No passkey configured";
	renderProfileResume(safeSettings.resume);
}

function renderProfileResume(resume) {
	const safeResume = resume || {};
	const size = safeResume.sizeBytes ? formatBytes(safeResume.sizeBytes) : "Unknown size";
	const available = Boolean(safeResume.available);

	elements.profileResumeSummary.innerHTML = `
		<strong>${escapeHtml(safeResume.fileName || "No resume configured")}</strong>
		<span>${escapeHtml(safeResume.message || "Resume details are unavailable.")}</span>
		<span class="muted">${escapeHtml(safeResume.path || "No path")} ${available ? `- ${size}` : ""}</span>
	`;
}

async function loadSentHistory() {
	const history = await requestJson(endpoints.sentHistory);
	renderSentHistory(history);
}

function renderSentHistory(history) {
	const dates = Object.keys(history || {});

	if (!dates.length) {
		elements.sentHistoryList.innerHTML = "<p class=\"muted\">No sent emails recorded yet.</p>";
		return;
	}

	elements.sentHistoryList.innerHTML = dates.map((date) => {
		const items = history[date] || [];
		return `
			<section class="sent-history-date">
				<h4>${escapeHtml(date)}</h4>
				<ul>${items.map(renderSentHistoryItem).join("")}</ul>
			</section>
		`;
	}).join("");
}

function renderSentHistoryItem(item) {
	const title = item.jobTitle || "Bulk outreach";
	const recipient = item.recipientEmail || arrayText(item.contactEmails) || "No recipient recorded";
	const recruiter = item.recruiterName ? ` - ${item.recruiterName}` : "";
	const subject = item.emailSubject ? `<span>${escapeHtml(item.emailSubject)}</span>` : "";
	const sentTime = item.sentAt ? new Date(item.sentAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "";

	return `
		<li>
			<strong>${escapeHtml(title)}${escapeHtml(recruiter)}</strong>
			<small>${escapeHtml(sentTime)} - ${escapeHtml(recipient)}</small>
			${subject}
		</li>
	`;
}

async function saveBulkDraft() {
	await withBusy("Saving bulk draft", async () => {
		state.bulkDraft = await persistBulkDraft();
		renderBulkDraft(state.bulkDraft);
		setBulkMessage("Saved changes to recipients, subject, and body template.");
		logActivity("Saved bulk email draft.");
	});
}

async function sendBulkFromModal() {
	const payload = readBulkDraftFromModal();
	const recipientCount = payload.recipients.length;

	if (recipientCount === 0) {
		setBulkMessage("Add at least one recipient before sending.", true);
		showBulkTab("recipients");
		return;
	}

	const confirmed = window.confirm(`Send bulk email to ${recipientCount} recipient${recipientCount === 1 ? "" : "s"} with the attached resume?`);
	if (!confirmed) {
		setBulkMessage("Bulk email send cancelled.");
		return;
	}

	await withBusy("Sending bulk emails", async () => {
		state.bulkDraft = await persistBulkDraft();
		renderBulkDraft(state.bulkDraft);
		const message = await requestText(endpoints.sendBulk, { method: "POST" });
		await loadSentHistory();
		setBulkMessage(message || "Bulk email send completed.");
		logActivity(message || "Bulk email send completed.");
	});
}

async function sendShortlistedJobs() {
	const shortlisted = state.matches;

	if (!shortlisted.length) {
		logActivity("No shortlisted jobs to email.");
		return;
	}

	const confirmed = window.confirm(`Send emails for ${shortlisted.length} shortlisted job${shortlisted.length === 1 ? "" : "s"} with the attached resume?`);
	if (!confirmed) {
		logActivity("Shortlisted email send cancelled.");
		return;
	}

	await withBusy("Sending shortlisted emails", async () => {
		const progress = startProcessConsole("Sending emails", [
			"Reading the shortlisted jobs currently kept on screen.",
			"Using your edited email body for each shortlisted job.",
			"Attaching the configured resume to every email.",
			"Sending emails through your configured SMTP account.",
			"Recording successfully sent jobs in sent-emails.json."
		]);
		try {
			const message = await requestText(endpoints.sendShortlisted, {
				method: "POST",
				headers: {
					Accept: "text/plain, application/json",
					"Content-Type": "application/json"
				},
				body: JSON.stringify(shortlisted.map(stripClientFields))
			});
			addConsoleLine(message || "Shortlisted email send completed.", "success");
			await loadSentHistory();
			logActivity(message || "Shortlisted email send completed.");
		} finally {
			stopProcessConsole(progress, "Done");
		}
	});
}

async function persistBulkDraft() {
	const payload = readBulkDraftFromModal();
	return requestJson(endpoints.bulkDraft, {
		method: "PUT",
		headers: {
			Accept: "application/json",
			"Content-Type": "application/json"
		},
		body: JSON.stringify(payload)
	});
}

function readBulkDraftFromModal() {
	const rows = [...elements.recipientTable.querySelectorAll(".recipient-row")];
	const recipients = rows.map((row) => {
		const inputs = row.querySelectorAll("input");
		return {
			email: inputs[0].value.trim(),
			name: inputs[1].value.trim()
		};
	}).filter((recipient) => recipient.email);

	return {
		recipients,
		subject: elements.bulkSubject.value.trim(),
		bodyTemplate: elements.bulkBody.value
	};
}

function setBulkMessage(message, isError = false) {
	elements.bulkMessage.textContent = message;
	elements.bulkMessage.classList.toggle("error", isError);
}

async function requestJson(url, options = {}) {
	const response = await fetch(url, {
		headers: { Accept: "application/json" },
		...options
	});

	if (!response.ok) {
		throw await toError(response);
	}

	return response.json();
}

async function requestText(url, options = {}) {
	const response = await fetch(url, {
		headers: { Accept: "text/plain, application/json" },
		...options
	});

	if (!response.ok) {
		throw await toError(response);
	}

	return response.text();
}

async function toError(response) {
	const contentType = response.headers.get("content-type") || "";

	if (contentType.includes("application/json")) {
		const body = await response.json();
		return new Error(body.message || body.error || response.statusText);
	}

	return new Error(await response.text() || response.statusText);
}

async function withBusy(label, work) {
	setStatus(label, "busy");
	setButtonsDisabled(true);

	try {
		await work();
		setStatus("Ready", "ready");
	} catch (error) {
		setStatus("Needs attention", "error");
		addConsoleLine(error.message || "Something went wrong.", "error");
		logActivity(error.message || "Something went wrong.");
	} finally {
		setButtonsDisabled(false);
	}
}

function setStatus(text, mode) {
	elements.status.lastChild.textContent = ` ${text}`;
	elements.pulse.classList.toggle("busy", mode === "busy");
	elements.pulse.classList.toggle("error", mode === "error");
}

function setButtonsDisabled(disabled) {
	elements.actionButtons.forEach((button) => {
		button.disabled = disabled;
	});
}

function renderProfile(profile) {
	elements.profileState.classList.add("hidden");
	elements.profileCard.classList.remove("hidden");
	elements.profileCard.innerHTML = `
		<h3>${escapeHtml(profile.name || "Candidate")}</h3>
		<p>${escapeHtml(joinPresent([profile.currentRole, profile.yearsOfExperience]).join(" • ") || "Profile ready")}</p>
		<p>${escapeHtml(profile.summary || "No summary returned.")}</p>
		<div class="chips">${renderChips(profile.skills || [], "match")}</div>
	`;
}

function renderRawJobs() {
	elements.jobsList.innerHTML = "";

	if (!state.jobs.length) {
		elements.jobsState.classList.remove("hidden");
		elements.jobsState.innerHTML = "<strong>No jobs found.</strong><span>Try again after checking your LinkedIn profile/session settings.</span>";
		return;
	}

	elements.jobsState.classList.add("hidden");
	state.jobs.forEach((job) => {
		elements.jobsList.appendChild(createJobCard({ job, matchPercentage: 0, matchedSkills: [], missingSkills: [] }, true));
	});
}

function renderMatches() {
	elements.jobsList.innerHTML = "";
	const filtered = state.matches.filter((match) => {
		if (state.activeFilter === "strong") {
			return Number(match.matchPercentage) >= 70;
		}

		if (state.activeFilter === "review") {
			return Number(match.matchPercentage) < 70;
		}

		return true;
	});

	if (!filtered.length) {
		elements.jobsState.classList.remove("hidden");
		elements.jobsState.innerHTML = "<strong>No matching cards for this filter.</strong><span>Switch filters or run Find Matches again.</span>";
		return;
	}

	elements.jobsState.classList.add("hidden");
	filtered.forEach((match) => {
		elements.jobsList.appendChild(createJobCard(match));
	});
}

function createJobCard(match, rawOnly = false) {
	const template = document.querySelector("#job-card-template");
	const card = template.content.firstElementChild.cloneNode(true);
	const job = match.job || {};
	const percentage = Number(match.matchPercentage) || 0;
	const title = job.jobTitle || "Untitled role";
	const location = arrayText(job.location);
	const contacts = arrayText(job.contactEmails);
	const meta = joinPresent([
		job.recruiterName,
		job.experienceRequired,
		location,
		contacts ? `Contacts: ${contacts}` : ""
	]).join(" • ");

	card.querySelector(".job-title").textContent = title;
	card.querySelector(".job-meta").textContent = meta || "No extra details returned.";

	const badge = card.querySelector(".match-badge");
	badge.textContent = rawOnly ? "Fetched" : `${percentage}%`;
	badge.classList.toggle("review", !rawOnly && percentage < 70);

	card.querySelector(".matched-skills").innerHTML = renderChips(match.matchedSkills || job.requiredSkills || [], rawOnly ? "" : "match");
	card.querySelector(".missing-skills").innerHTML = renderChips(match.missingSkills || [], "missing") || "<span class=\"muted\">None listed</span>";

	const details = card.querySelector(".email-preview");
	if (rawOnly || (!match.emailSubject && !match.emailBody)) {
		details.remove();
	} else {
		card.querySelector(".email-subject").textContent = match.emailSubject || "No subject returned.";
		const bodyEditor = card.querySelector(".email-body-editor");
		bodyEditor.value = match.emailBody || "";
		bodyEditor.addEventListener("input", () => {
			match.emailBody = bodyEditor.value;
		});
	}

	const deleteButton = card.querySelector(".delete-match");
	if (rawOnly) {
		deleteButton.remove();
	} else {
		deleteButton.classList.remove("hidden");
		deleteButton.addEventListener("click", () => {
			state.matches = state.matches.filter((item) => item.clientId !== match.clientId);
			state.jobs = state.matches.map((item) => item.job).filter(Boolean);
			renderMetrics();
			renderMatches();
			logActivity(`Removed ${title} from shortlisted jobs.`);
		});
	}

	return card;
}

function withMatchIds(matches) {
	return matches.map((match, index) => ({
		...match,
		clientId: `${Date.now()}-${index}`
	}));
}

function stripClientFields(match) {
	const { clientId, ...payload } = match;
	return payload;
}

function renderMetrics() {
	elements.metricJobs.textContent = state.jobs.length;
	elements.metricMatches.textContent = state.matches.length;

	if (!state.matches.length) {
		elements.metricAverage.textContent = "0%";
		return;
	}

	const average = Math.round(state.matches.reduce((sum, match) => sum + Number(match.matchPercentage || 0), 0) / state.matches.length);
	elements.metricAverage.textContent = `${average}%`;
}

function renderChips(items, variant = "") {
	if (!items.length) {
		return "<span class=\"muted\">None listed</span>";
	}

	return items.map((item) => `<span class="chip ${variant}">${escapeHtml(item)}</span>`).join("");
}

function logActivity(message) {
	const item = document.createElement("li");
	item.textContent = `${new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })} - ${message}`;
	elements.activityLog.prepend(item);

	while (elements.activityLog.children.length > 6) {
		elements.activityLog.lastElementChild.remove();
	}
}

function startProcessConsole(label, steps) {
	clearProcessConsole();
	elements.consoleState.textContent = label;
	addConsoleLine(`${label} started.`, "active");

	let index = 0;
	const timer = window.setInterval(() => {
		addConsoleLine(steps[index % steps.length], "active");
		index += 1;
	}, 1800);

	addConsoleLine(steps[index], "active");
	index += 1;

	return timer;
}

function stopProcessConsole(timer, stateLabel) {
	window.clearInterval(timer);
	elements.consoleState.textContent = stateLabel;
}

function clearProcessConsole() {
	elements.processConsole.innerHTML = "";
}

function addConsoleLine(message, tone = "") {
	const item = document.createElement("li");
	const time = new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
	item.className = tone;
	item.innerHTML = `<span>${time}</span>${escapeHtml(message)}`;
	elements.processConsole.appendChild(item);

	while (elements.processConsole.children.length > 9) {
		elements.processConsole.firstElementChild.remove();
	}

	elements.processConsole.scrollTop = elements.processConsole.scrollHeight;
}

function joinPresent(values) {
	return values.filter((value) => value && String(value).trim());
}

function arrayText(value) {
	return Array.isArray(value) ? value.filter(Boolean).join(", ") : value || "";
}

function escapeHtml(value) {
	return String(value)
		.replaceAll("&", "&amp;")
		.replaceAll("<", "&lt;")
		.replaceAll(">", "&gt;")
		.replaceAll('"', "&quot;")
		.replaceAll("'", "&#039;");
}

function escapeAttribute(value) {
	return escapeHtml(value).replaceAll("\n", " ");
}

function formatBytes(bytes) {
	const units = ["B", "KB", "MB", "GB"];
	let value = Number(bytes);
	let unitIndex = 0;

	while (value >= 1024 && unitIndex < units.length - 1) {
		value /= 1024;
		unitIndex += 1;
	}

	return `${value.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`;
}

import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { Type } from "typebox";
import TurndownService from "turndown";

const domino = require("domino");

const READ_WEBSITE_PARAMS = Type.Object({
	url: Type.String({ description: "The http(s) URL to read and convert to markdown" }),
});

function htmlToMarkdown(html: string): string {
	const withoutScripts = html
		.replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, "")
		.replace(/<style\b[^<]*(?:(?!<\/style>)<[^<]*)*<\/style>/gi, "");

	const window = domino.createWindow(withoutScripts);
	const { document } = window;
	const title = document.title?.trim() ?? "";
	const preferredRoot =
		document.querySelector("article") ??
		document.querySelector("main") ??
		document.querySelector(".md-content__inner") ??
		document.body;
	const content = preferredRoot.cloneNode(true);

	if (content instanceof window.HTMLElement) {
		content
			.querySelectorAll(
				[
					"nav",
					"header",
					"footer",
					"aside",
					"script",
					"style",
					".md-sidebar",
					".md-header",
					".md-footer",
					".md-search",
					".md-banner",
					".md-tabs",
					".md-source",
					".headerlink",
					"a[id^='__codelineno']",
					"a[href^='#__codelineno']",
					".copy-to-clipboard-button",
					".linenos",
					".lineno",
				].join(", ")
			)
			.forEach((element: Element) => element.remove());
	}

	const turndown = new TurndownService({
		headingStyle: "atx",
		codeBlockStyle: "fenced",
		bulletListMarker: "-",
	});

	const markdown = turndown.turndown(content instanceof window.HTMLElement ? content.innerHTML : preferredRoot.outerHTML).trim();
	return title ? `# ${title}\n\n${markdown}` : markdown;
}

export default function readWebsiteExtension(pi: ExtensionAPI) {
	pi.registerTool({
		name: "read_website",
		label: "Read Website",
		description: "Fetch a public web page and convert its HTML to markdown using Turndown.",
		promptSnippet: "Fetch a URL and return readable markdown converted from the page HTML",
		promptGuidelines: [
			"Use read_website when the user asks to read, summarize, inspect, or extract content from a web page URL.",
		],
		parameters: READ_WEBSITE_PARAMS,
		async execute(_toolCallId, params, signal) {
			let parsedUrl: URL;
			try {
				parsedUrl = new URL(params.url);
			} catch {
				return {
					content: [{ type: "text", text: `Invalid URL: ${params.url}` }],
					isError: true,
				};
			}

			if (parsedUrl.protocol !== "http:" && parsedUrl.protocol !== "https:") {
				return {
					content: [{ type: "text", text: "read_website only supports http and https URLs." }],
					isError: true,
				};
			}

			const response = await fetch(parsedUrl, {
				signal,
				headers: {
					accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
					"user-agent": "pi-read-website/1.0",
				},
			});

			if (!response.ok) {
				return {
					content: [{ type: "text", text: `Failed to fetch ${parsedUrl}: HTTP ${response.status} ${response.statusText}` }],
					isError: true,
					details: { status: response.status, statusText: response.statusText },
				};
			}

			const html = await response.text();
			const markdown = htmlToMarkdown(html);

			return {
				content: [{ type: "text", text: markdown || "No readable markdown content found." }],
				details: {
					url: parsedUrl.toString(),
					status: response.status,
					contentType: response.headers.get("content-type"),
					htmlLength: html.length,
					markdownLength: markdown.length,
				},
			};
		},
	});
}

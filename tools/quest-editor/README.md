# Branz Quest Director

Local-only visual authoring surface for the quest, dialogue, cutscene, and
migration YAML schemas. It never connects to a production server and exports
validated files with a `.staging.yml` suffix so promotion remains explicit.

Run `npm install`, then `npm run dev`. Use `npm run build` for the local release
artifact in `dist/`.

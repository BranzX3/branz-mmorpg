# Physical Gameplay Item Acceptance Environment Gate

This gate keeps the real A-F client pass reproducible without changing the accepted gameplay/runtime
revision.

The repository's `mmo-bootstrap:runServer` task currently selects Minecraft `26.2` but does not pin a
Paper build number. The run-task plugin therefore resolves its default `Latest` build. The plugin does
support an explicit build number, but pinning only the accepted runtime would not pin the exact
pre-PR #21 legacy runtime used by section A and would also create a new runtime revision.

For this acceptance cycle, environment consistency is therefore evidence, not an unreviewed runtime
change.

## Required fingerprint

Before seeding section A0, record:

- the complete Paper startup identity line, including Minecraft version, Paper build number and Paper
  commit identity;
- the complete Java runtime identity used to launch Gradle/Paper (`java -version` plus the effective
  `JAVA_HOME`);
- Minecraft client version;
- operating system identity sufficient to show A0 and A1 run on the same acceptance machine.

The automated post-merge validation of runtime revision
`0851f599caf8565d78338a53c9917f9c982d6f4a` used Paper 26.2 build 112. That is historical validation
evidence, **not** permission to silently substitute build 112 for the actual Paper build resolved
when the human A-F pass begins.

## Consistency rule

At every Paper boot used by A0, A1, B-F, reconnect/restart verification or the isolated A negative
case, compare the startup environment against the first accepted fingerprint.

The pass is valid only when all of these remain identical for the entire A-F run:

1. Paper Minecraft version;
2. Paper build number and Paper commit identity;
3. Java runtime version/vendor/build and effective `JAVA_HOME`;
4. Minecraft client version;
5. acceptance machine/OS identity.

If any value changes, stop the pass. Do not mix evidence from before and after the environment change.
Start a fresh A-F evidence record from A0 using one consistent environment.

## Why this does not change the runtime revision

This gate records the environment in which runtime revision
`0851f599caf8565d78338a53c9917f9c982d6f4a` is exercised. It does not alter source, migrations,
configuration defaults or active content.

If the project later decides to pin an exact Paper build in Gradle/runtime configuration, that is a
runtime change. It must receive its own source revision, automated build/smoke validation and a newly
pinned live-acceptance runtime revision before A-F is run again.

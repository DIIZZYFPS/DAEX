# DAEX Agent Profiles

Specialized agent profiles for the DaexLlama JNI bridge development workflow. Each agent has a focused role in the build-test-debug cycle.

## Profiles

| Agent | File | Role |
|-------|------|------|
| Hypothesis | `hypothesis-agent.md` | Root cause analysis, testable hypotheses |
| Research | `research-agent.md` | llama.cpp source validation, API contract verification |
| Web Research | `web-research-agent.md` | Live web search, version tracking, docs parsing |
| Build | `build-agent.md` | CMake/NDK pipeline, backend integration, ABI config |
| Testing | `testing-agent.md` | Test suite design, logcat analysis, performance metrics |
| Code Review | `code-review-agent.md` | Static analysis, memory safety, JNI contract audit |

## How to Use

Load an agent profile with:
```
skill_view(name='daex-<agent-name>')
```

Or delegate work to a specific agent by loading its profile and providing context.

## Orchestration Patterns

### Pattern 1: Bug Investigation
When the bridge breaks:

1. **Hypothesis Agent** — Generate ranked hypotheses about the root cause
2. **Research Agent** — Validate hypotheses against llama.cpp source
3. **Code Review Agent** — Audit the suspected code path for specific issues
4. **Apply Fix** — Make the targeted change
5. **Code Review Agent** — Review the fix before committing
6. **Testing Agent** — Run verification suite to confirm fix

### Pattern 2: New Feature Integration
When adding a new llama.cpp feature:

1. **Web Research Agent** — Check for latest llama.cpp docs, release notes, API changes
2. **Research Agent** — Verify API against local llama.cpp source
3. **Build Agent** — Update CMakeLists.txt if new dependencies needed
4. **Implement** — Write the bridge code
5. **Code Review Agent** — Audit the new code
6. **Testing Agent** — Add test cases for the new feature

### Pattern 3: Version Upgrade
When updating llama.cpp submodule or NDK version:

1. **Web Research Agent** — Find release notes, changelog, breaking changes
2. **Research Agent** — Cross-reference changes with local source
3. **Build Agent** — Update CMakeLists.txt for any API changes
4. **Code Review Agent** — Audit for deprecated API usage
5. **Testing Agent** — Run full verification suite

### Pattern 4: Pre-Merge Gate
Before merging any bridge changes:

1. **Code Review Agent** — Full static analysis audit
2. **Testing Agent** — Run verification suite
3. **Build Agent** — Verify clean build on target ABI
4. **Approve** — Only if all checks pass

### Pattern 5: Backend Integration
When adding or switching backends (CPU → KleidiAI → Hexagon):

1. **Research Agent** — Document backend requirements and API changes
2. **Build Agent** — Update CMakeLists.txt, add backend .so files
3. **Testing Agent** — Verify backend activation, measure performance
4. **Code Review Agent** — Audit for backend-specific issues

## Agent Communication

Agents share context through structured output. Key data formats:

### Hypothesis → Research
```
VERIFY: [hypothesis description]
Focus: [specific function/API]
Evidence: [what to look for]
```

### Research → Hypothesis
```
FINDING: [what the source actually shows]
GAP: [bridge code vs actual behavior]
IMPACT: [does this explain the symptom?]
```

### Testing → Hypothesis
```
RESULT: [test passed/failed]
OBSERVATION: [what happened]
SUSPICIOUS: [any unexpected behavior]
```

### Code Review → All
```
ISSUE: [severity] [location] [description]
FIX: [specific change]
BLOCKING: [yes/no]
```

## Workflow Example: Reproducing a Real Issue

Scenario: Bridge compiles but model generates garbage output.

```
USER: "Bridge compiles but output is garbage. Help me investigate."

AGENT: Loads hypothesis-agent.

HYPOTHESIS:
  H1: Chat template format mismatch (conf)
  H2: Tokenizer not using BOS/EOS correctly
  H3: Batch position tracking wrong
  H4: Sampler temperature too high
  H5: Model file corrupted or wrong quantization

RESEARCH:
  Loads research-agent to verify common_tokenize behavior with
  chat templates, check if BOS/EOS are handled correctly.

FINDING: common_tokenize with add_special=false doesn't add BOS.
  Bridge calls common_tokenize(ctx, formatted, false, false).
  If chat template already includes BOS, this is correct.
  If chat template doesn't include BOS, we're missing it.

HYPOTHESIS (refined):
  H2: Chat template doesn't include BOS, tokenizer called with false.
  Test: Check if model's chat template includes BOS.
  If no, change common_tokenize call to add_special=true.

CODE REVIEW:
  Reviews the proposed fix. Confirms it's safe.

TESTING:
  Runs test with small model. Verifies output is coherent.

RESULT: Fixed.
```

## When NOT to Use Agents

- Simple typo fixes — just fix it
- Documentation updates — write them directly
- Dependency version bumps — do it yourself
- UI changes — not in scope for these agents

## Agent Limits

Each agent runs in isolation with no cross-session memory. Always provide:
- The relevant file paths
- The specific function or area of concern
- Any error messages or logcat output
- What you've already tried

## Adding New Agents

To add a new agent profile:
1. Create a new `.md` file in this directory
2. Follow the same structure: Role, When to Use, Capabilities, Working Method, Output Format, Constraints
3. Add it to the table above
4. Update orchestration patterns if it changes the workflow

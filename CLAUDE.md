# Play Against AI

## Running
- Backend build/test: `cd backend && ./gradlew test`
- Backend run: `cd backend && ./gradlew run`
- Frontend dev: `cd frontend && npm run dev`

## Code Style
- Fail loudly: throw exceptions for invariant violations rather than silently returning defaults. If a code path should be unreachable, use `error()` to make that explicit.

# Architecture

## Core
`GameModule<S,A>` separates a game's state/action model from the automation engine.

## Game
Block Puzzle owns:
- board rules
- piece definitions
- scoring
- line clearing
- legal action generation

## AI
`BlockAi` receives exact state and enumerates legal placements. It uses bounded look-ahead and a beam of candidate states to keep mobile computation predictable.

## Future modules
A second game should implement:

```kotlin
class Game002 : GameModule<Game002State, Game002Action>
```

No changes should be required in the Block Puzzle engine.

## Performance
The UI is not in the decision loop. The AI works on immutable snapshots/copies. The loop records microsecond-level latency so optimization can be measured rather than guessed.

export type GamePhase = 'WAITING' | 'PRE_FLOP' | 'FLOP' | 'TURN' | 'RIVER' | 'SHOWDOWN' | 'HAND_COMPLETE';
export type ActionType = 'fold' | 'check' | 'call' | 'raise' | 'all_in';

export interface CardDto {
  rank: string;
  suit: string;
}

export interface PlayerDto {
  index: number;
  name: string;
  chips: number;
  currentBet: number;
  isFolded: boolean;
  isAllIn: boolean;
  isSittingOut: boolean;
  isDealer: boolean;
  holeCards: CardDto[] | null;
  lastAction: string | null;
  playerType: string | null;
  position: string;
  isThinking: boolean;
}

export interface WinnerDto {
  playerIndex: number;
  playerName: string;
  amount: number;
  handDescription: string;
}

export interface HoleCardsDto {
  playerIndex: number;
  cards: CardDto[];
  mucked?: boolean;
}

export interface GameStateUpdate {
  type: 'game_state';
  phase: GamePhase;
  communityCards: CardDto[];
  pot: number;
  players: PlayerDto[];
  dealerIndex: number;
  currentPlayerIndex: number;
  isUserTurn: boolean;
  minimumRaise: number;
  callAmount: number;
  handNumber: number;
  showAiCards: boolean;
  showPlayerTypes: boolean;
  ante: number;
  gameLabel: string | null;
}

export interface ActionPerformed {
  type: 'action_performed';
  playerIndex: number;
  playerName: string;
  action: string;
  phase: GamePhase;
}

export interface HandResult {
  type: 'hand_result';
  winners: WinnerDto[];
  allHoleCards: HoleCardsDto[];
  summary: string;
}

export interface PlayerEliminated {
  type: 'player_eliminated';
  playerIndex: number;
  playerName: string;
}

export interface PlayerReloaded {
  type: 'player_reloaded';
  playerIndex: number;
  playerName: string;
  chips: number;
}

export interface PlayerJoined {
  type: 'player_joined';
  playerIndex: number;
  playerName: string;
  chips: number;
}

export interface TournamentUpdate {
  type: 'tournament_update';
  remainingPlayers: number;
  totalPlayers: number;
  blindLevel: number;
  smallBlind: number;
  bigBlind: number;
  ante: number;
  handsUntilNextLevel: number;
}

export interface TournamentFinished {
  type: 'tournament_finished';
  finishPosition: number;
  totalPlayers: number;
}

export interface ServerError {
  type: 'error';
  message: string;
}

// Game config types
export type CashStakes = 'ONE_TWO' | 'TWO_FIVE' | 'FIVE_TEN';
export type TournamentBuyin = 'HUNDRED' | 'FIVE_HUNDRED' | 'FIFTEEN_HUNDRED';

export type GameConfig =
  | { type: 'cash'; stakes: CashStakes; rakeEnabled: boolean; tableSize: number }
  | { type: 'tournament'; buyin: TournamentBuyin; playerCount: number; antesEnabled: boolean; tableSize: number; startingBBs: number };

export type ServerMessage =
  | GameStateUpdate
  | ActionPerformed
  | HandResult
  | PlayerEliminated
  | PlayerReloaded
  | PlayerJoined
  | TournamentUpdate
  | TournamentFinished
  | ServerError;

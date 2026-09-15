// Mock expo-secure-store globally so tests don't depend on the native module.
jest.mock('expo-secure-store', () => {
  const store = new Map();
  return {
    getItemAsync: jest.fn(async (k) => (store.has(k) ? store.get(k) : null)),
    setItemAsync: jest.fn(async (k, v) => {
      store.set(k, v);
    }),
    deleteItemAsync: jest.fn(async (k) => {
      store.delete(k);
    }),
    AFTER_FIRST_UNLOCK: 'AFTER_FIRST_UNLOCK',
    __reset: () => store.clear(),
  };
});

// Quiet down act() warnings for async fetches in some components.
global.IS_REACT_ACT_ENVIRONMENT = true;

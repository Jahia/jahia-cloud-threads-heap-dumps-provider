/* Minimal @apollo/client mock. Tests override useQuery/useMutation per case. */
export const gql = (...args) => args;

export const useQuery = jest.fn(() => ({data: undefined, loading: false}));

export const useMutation = jest.fn(() => [jest.fn(() => Promise.resolve({data: {}})), {loading: false}]);

import { createContext, useContext, useState } from "react";

const SelectionContext = createContext();

export function useSelection() {
  return useContext(SelectionContext);
}

export function SelectionProvider({ children }) {
  const [checkedFiles, setCheckedFiles] = useState(new Set());
  const [lastCheckedIndex, setLastCheckedIndex] = useState(null);

  return (
    <SelectionContext.Provider
      value={{
        checkedFiles,
        setCheckedFiles,
        lastCheckedIndex,
        setLastCheckedIndex,
      }}
    >
      {children}
    </SelectionContext.Provider>
  );
}

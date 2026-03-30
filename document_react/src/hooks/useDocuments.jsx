// useDocuments.ts
import { useContext } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";

import { AuthContext } from "src/context/AuthContext";

export function useDocuments() {
  const { user, isLoggedIn } = useContext(AuthContext);
  const [params] = useSearchParams();
  const q = params.get("q") || ""; // e.g. "foo,bar"
  const kwList = q.split(",").filter(Boolean); // ["foo","bar"]

  return useQuery({
    // include `q` (or `kwList`) in your key so React-Query knows to refetch
    queryKey: ["documents", user?.id, q],
    queryFn: async () => {
      const url = new URL("http://localhost:8180/api/v1/documents");
      url.searchParams.set("userId", user.id);
      url.searchParams.set("role", user.role);
      // append each keyword
      kwList.forEach((k) => url.searchParams.append("keyword", k));
      const res = await fetch(url.toString());
      if (!res.ok) throw new Error(`Error ${res.status}`);
      return res.json();
    },
    enabled: !!user?.id && isLoggedIn,
    keepPreviousData: true,
    staleTime: 1000 * 60 * 5,
    retry: 1,
  });
}

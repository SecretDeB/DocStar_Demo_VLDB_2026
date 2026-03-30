import { use } from "react";
import { Navigate } from "react-router-dom";

import { AuthContext } from "src/context/AuthContext";


export default function Homepage() {
  const { user } = use(AuthContext);

  if (user.role === "Admin")
    return <Navigate to="/admin" replace />;
  else
    return <Navigate to="/documents" replace />;
}

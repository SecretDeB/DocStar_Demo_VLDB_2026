import { useContext } from "react";
import { Navigate, Outlet } from "react-router-dom";

import { AuthContext } from "src/context/AuthContext";

export default function AdminRoute() {
  const { user } = useContext(AuthContext);

  // Redirect to login if not authenticated
  if (!user) {
    return <Navigate to="/login" replace />;
  }

  // Redirect to documents if not admin
  if (user.role !== "Admin") { // Adjust based on your user object
    return <Navigate to="/admin" replace />;
  }

  return <Outlet />;
}
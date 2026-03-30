import { createBrowserRouter, RouterProvider } from "react-router-dom";

import "./App.css";
import Auth from "./components/pages/authentication/Auth";
import Layout from "./Layout";
import Homepage from "./components/pages/Homepage/Homepage";
import ProtectedRoute from "./components/ProtectedRoute";
import AdminRoute from "./components/AdminRoute";
import ErrorPage from "./components/pages/ErrorPage/ErrorPage";
import SearchDocuments from "./components/pages/client/SearchDocuments";
import AdminDashboard from "./components/pages/dbo/AdminDashboard";
import DocumentsDashboard from "./components/pages/dbo/DocumentsDashboard";
import Keywords from "./components/pages/dbo/Keywords";
import AccessDashboard from "./components/pages/dbo/AccessDashboard";
import UploadDocuments from "./components/pages/dbo/UploadDocuments";

const router = createBrowserRouter([
  // Public route - no layout
  {
    path: "/login",
    element: <Auth />,
  },

  // Protected routes - with layout
  {
    path: "/",
    element: <Layout />,
    errorElement: <ErrorPage />,
    children: [
      // Homepage (public or protected - your choice)
      {
        index: true,
        element: <Homepage />
      },

      // Client routes (protected - any authenticated user)
      {
        path: "documents",
        element: <ProtectedRoute />,
        children: [
          {
            path: ":search?",
            element: <SearchDocuments />,
          },
        ],
      },

      // Admin routes (protected - admin only)
      {
        path: "admin",
        element: <AdminRoute />,
        children: [
          {
            index: true,
            element: <AdminDashboard />, // Default admin page
          },
          {
            path: "documents",
            element: <DocumentsDashboard />,
          },
          {
            path: "keywords",
            element: <Keywords />,
          },
          {
            path: "access",
            element: <AccessDashboard />,
          },
          {
            path: "upload",
            element: <UploadDocuments />,
          },
        ],
      },
    ],
  },
]);

function App() {
  return (
    <div className="App">
      <RouterProvider router={router} />
    </div>
  );
}

export default App;

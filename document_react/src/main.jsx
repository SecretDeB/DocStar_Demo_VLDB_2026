import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App.jsx";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "./context/AuthContext.jsx";
import { MantineProvider } from "@mantine/core";
import { GoogleOAuthProvider } from "@react-oauth/google";
import { Toaster } from "react-hot-toast";

const queryClient = new QueryClient();

createRoot(document.getElementById("root")).render(
  <QueryClientProvider client={queryClient}>
    <GoogleOAuthProvider clientId="494971453837-uqghuk4mpk4eculvomkra2eredqb9i8f.apps.googleusercontent.com">
      <AuthProvider>
        <MantineProvider>
          <Toaster toastOptions={{
            style: {
              // Set your desired max-width in pixels or any other CSS unit
              maxWidth: '500px',
              fontSize: '18px',
              minHeight: '40px',
              width: 'fit-content' // Optional: adjust width to fit content within maxWidth
            },
            success: { duration: 10000 },
            error: { duration: 10000 },
            loading: { duration: Infinity }
          }} position='top-center'
            reverseOrder={false} />
          <App />
        </MantineProvider>
      </AuthProvider>
    </GoogleOAuthProvider>
  </QueryClientProvider>
);

import { createContext, useState } from "react";
import toast from "react-hot-toast";

const AuthContext = createContext();

function initializeUser() {
  const storedUser = localStorage.getItem("user");
  return storedUser
    ? JSON.parse(storedUser)
    : {
      name: "",
      username: "",
      password: "",
      address: "",
      phone: "",
      role: "",
    };
}

function initializeLogin() {
  return localStorage.getItem("user") ? true : false;
}

function AuthProvider({ children }) {
  const [user, setUser] = useState(initializeUser);

  const [errors, setErrors] = useState({
    name: "",
    username: "",
    password: "",
    address: "",
    phone: "",
  });

  const [isLoggedIn, setIsLoggedIn] = useState(initializeLogin);
  const [authAction, setAuthAction] = useState("login");

  function resetUser() {
    setUser((prevUser) => ({
      ...prevUser,
      name: "",
      username: "",
      password: "",
      address: "",
      phone: "",
    }));
  }

  async function resetErrors() {
    setErrors((prevErrors) => ({
      ...prevErrors,
      name: "",
      username: "",
      password: "",
      phone: "",
      address: "",
      login: "",
    }));
  }

  const handleLogin = async (user) => {
    const response = await fetch("http://localhost:8180/api/v1/auth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ username: user.username, password: user.password }),
    });

    console.log(response.status);

    if (response.status === 200) {
      const result = await response.json();
      console.log(result);
      setUser(result);
      setIsLoggedIn(true);
      localStorage.setItem("user", JSON.stringify(result));
      return "SUCCESS";
    }
    else if (response.status === 403) {
      const result = await response.json();
      if (result.error === "INCORRECT_PASSWORD") {
        toast.error("Please enter the correct password!");
      }
      else if (result.error === "USER_NOT_FOUND")
        toast.error("User not found");
      return "FAILURE";
    }
  }

  const handleSignUp = async (user) => {
    const response = await fetch("http://localhost:8180/api/v1/auth/signup", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        name: user.name,
        email: user.email,
        username: user.username,
        password: user.password,
        phone: user.phone,
        address: user.address
      }),
    });


    if (response.status === 200) {
      const result = await response.json();
      setUser(result);
      setIsLoggedIn(true);
      localStorage.setItem("user", JSON.stringify(result));
      return "SUCCESS";
    }
    else if (response.status === 409) {
      const result = await response.json();
      if (result.error === "USERNAME_ALREADY_EXISTS")
        toast.error("Username already exists");
      else if (result.error === "EMAIL_ALREADY_EXISTS")
        toast.error("Email already exists");
      return result.error;
    }
  }

  const handleGoogleLogin = async ({ email }) => {

    const response = await fetch(`http://localhost:8180/api/v1/auth/google`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ email: email })
    })

    if (response.status === 200) {
      const result = await response.json();
      console.log(result);
      setUser(result);
      setIsLoggedIn(true);
      localStorage.setItem("user", JSON.stringify(result));
      return "SUCCESS";
    }
    else if (response.status === 403) {
      toast.error("User not found, please sign up first!");
      return "USER_NOT_FOUND";
    }
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        setUser,
        resetUser,
        isLoggedIn,
        setIsLoggedIn,
        authAction,
        setAuthAction,
        handleLogin,
        handleGoogleLogin,
        handleSignUp,
        errors,
        setErrors,
        resetErrors,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export { AuthContext, AuthProvider };

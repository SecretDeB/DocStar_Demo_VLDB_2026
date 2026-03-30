import { use } from "react";
import { jwtDecode } from "jwt-decode";
import { BookCopy } from "lucide-react";
import LoginForm from "src/components/pages/authentication/LoginForm";
import SignupForm from "src/components/pages/authentication/SignupForm";
import styles from "./auth.module.css";

import { useNavigate } from "react-router-dom";
import { GoogleLogin } from "@react-oauth/google";

import { AuthContext } from "src/context/AuthContext";

export default function Auth() {
  const {
    authAction,
    setAuthAction,
    handleLogin,
    handleGoogleLogin,
    handleSignUp,
    user,
    setUser,
    resetUser,
    setErrors,
    resetErrors,
  } = use(AuthContext);

  const navigate = useNavigate();

  const handleGoogleSuccess = async (credentialResponse) => {
    const decodedToken = jwtDecode(credentialResponse.credential);

    const { email, name } = decodedToken;
    const result = await handleGoogleLogin({ email: email });
    if (result === "USER_NOT_FOUND") {
      setUser({ ...user, name: name, email: email });
      setAuthAction("signup");
      return;
    }
    if (user.role === "Admin") navigate("/admin/dashboard")
    navigate("/");
  };

  const validateForm = () => {
    const newErrors = {};
    if (authAction === "signup") {
      if (!user.name) newErrors.name = "Name is required";
      if (!user.phone) newErrors.phone = "Phone number is required";
      if (!user.address) newErrors.address = "Address is required";
    }
    if (!user.username) newErrors.username = "Username is required";
    if (!user.password) newErrors.password = "Password is required";

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return false;
    }
    return true;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    resetErrors();
    if (validateForm()) {
      if (authAction === "login") {
        const result = await handleLogin(user);
        if (result === "FAILURE") {
          return;
        }
      }
      else if (authAction === "signup") {
        const result = await handleSignUp(user);
        if (result === 'USERNAME_ALREADY_EXISTS') {
          setErrors({
            username: 'Username already taken'
          })
          return;
        }
        else if (result === "EMAIL_ALREADY_EXISTS") {
          setErrors({
            email: 'Email already registered'
          })
          return;
        }
      }
      if (user.role === "Admin") navigate("/admin/dashboard")
      navigate("/");
    }
  };

  const handleSwitchAction = (action) => {
    setAuthAction(action);
    resetUser();
    resetErrors();
  };

  return (
    <div className={styles.authContainer}>
      <div className={styles.formWrapper}>
        <div className={styles.logoContainer}>
          <BookCopy className={styles.logo} />
          <h1 className={styles.title}>DocFinder</h1>
        </div>

        <form onSubmit={handleSubmit}>
          {authAction === "login" ? <LoginForm /> : <SignupForm />}

          <div className={styles.buttonContainer}>
            <button
              type={authAction === "login" ? "submit" : "button"}
              className={
                authAction === "login"
                  ? styles.submit
                  : `${styles.submit} ${styles.inactive}`
              }
              onClick={() =>
                authAction !== "login" && handleSwitchAction("login")
              }
            >
              Log In
            </button>
            <button
              type={authAction === "signup" ? "submit" : "button"}
              className={
                authAction === "signup"
                  ? styles.submit
                  : `${styles.submit} ${styles.inactive}`
              }
              onClick={() =>
                authAction !== "signup" && handleSwitchAction("signup")
              }
            >
              Sign Up
            </button>
          </div>
        </form>
        <div className={styles.googleLogin}><GoogleLogin onSuccess={(credentialResponse) => { handleGoogleSuccess(credentialResponse) }} onFail={() => {
          console.log('Failed');
        }} onError={() => console.log('Error')} /></div>

      </div>
    </div>
  );
}

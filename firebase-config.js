// Import the functions you need from the SDKs you need
import { initializeApp } from "firebase/app";
import { getAnalytics } from "firebase/analytics";
// TODO: Add SDKs for Firebase products that you want to use
// https://firebase.google.com/docs/web/setup#available-libraries

// Your web app's Firebase configuration
// For Firebase JS SDK v7.20.0 and later, measurementId is optional
export const firebaseConfig = {
  apiKey: "AIzaSyCb2dT7XqWl9D5CPxvvx4QDXX4OYQ3uAOg",
  authDomain: "attention-b0e42.firebaseapp.com",
  databaseURL: "https://attention-b0e42-default-rtdb.firebaseio.com",
  projectId: "attention-b0e42",
  storageBucket: "attention-b0e42.firebasestorage.app",
  messagingSenderId: "25988634025",
  appId: "1:25988634025:web:22fca4e4cacff857abe0b8",
  measurementId: "G-0RQV6K6GXJ"
};

// Initialize Firebase
export const app = initializeApp(firebaseConfig);
export const analytics = typeof window !== "undefined" ? getAnalytics(app) : null;

if (typeof window !== "undefined") {
  window.firebaseConfig = firebaseConfig;
  window.firebaseApp = app;
}

export default app;

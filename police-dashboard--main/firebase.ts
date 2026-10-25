
import { initializeApp } from "firebase/app";
import { getAnalytics } from "firebase/analytics";
import { getAuth } from "firebase/auth";
import { getDatabase } from "firebase/database";

// Your web app's Firebase configuration
const firebaseConfig = {
    apiKey: "AIzaSyCxdoqiRqxi7GtiwlTPKmrVtSBCeGg0JAQ",
    authDomain: "akatsuki-98705.firebaseapp.com",
    databaseURL: "https://akatsuki-98705-default-rtdb.firebaseio.com",
    projectId: "akatsuki-98705",
    storageBucket: "akatsuki-98705.firebasestorage.app",
    messagingSenderId: "65870867069",
    appId: "1:65870867069:web:2d45bfa0e1ef2a986fdf14",
    measurementId: "G-RCPWM8PT8X"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const analytics = getAnalytics(app);
export const auth = getAuth(app);
export const database = getDatabase(app);

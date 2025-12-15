// ========================================
// MainScript.js
// 古明地こいしアプリのメイン機能
// ========================================

/**
 * こいしちゃんの表情を変更する関数
 * @param {string} Url - 画像のURL
 */
function KoishiFaceUpdate(Url) {
    const koisiImg = document.getElementById("koisi");
    if (koisiImg) {
        koisiImg.src = Url;
    }
}

/**
 * JSONデータを取得する関数
 * @param {string} url - 取得するJSONのURL
 * @returns {Promise<Object>} - JSONオブジェクト
 */
function GetJson(url) {
    return fetch(url)
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            return data;
        })
        .catch(error => {
            console.error('Error fetching JSON:', error);
            return null;
        });
}

/**
 * 配列をシャッフルする関数（Fisher-Yatesアルゴリズム）
 * @param {Array} array - シャッフルする配列
 * @returns {Array} - シャッフルされた配列
 */
function shuffleArray(array) {
    const newArray = [...array];
    for (let i = newArray.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [newArray[i], newArray[j]] = [newArray[j], newArray[i]];
    }
    return newArray;
}

/**
 * ランダムな整数を生成する関数
 * @param {number} min - 最小値
 * @param {number} max - 最大値
 * @returns {number} - ランダムな整数
 */
function getRandomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

/**
 * 日付を見やすい形式にフォーマットする関数
 * @param {Date} date - フォーマットする日付オブジェクト
 * @returns {string} - フォーマットされた日付文字列
 */
function formatDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    
    return `${year}/${month}/${day} ${hours}:${minutes}:${seconds}`;
}

/**
 * オブジェクトを深くコピーする関数
 * @param {Object} obj - コピーするオブジェクト
 * @returns {Object} - コピーされたオブジェクト
 */
function deepCopy(obj) {
    return JSON.parse(JSON.stringify(obj));
}

/**
 * 遅延処理を行う関数（await と一緒に使います）
 * @param {number} ms - 遅延時間（ミリ秒）
 * @returns {Promise} - Promise オブジェクト
 */
function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

/**
 * ローカルストレージにデータを保存する関数
 * @param {string} key - 保存するキー
 * @param {*} value - 保存する値
 */
function saveToLocalStorage(key, value) {
    try {
        localStorage.setItem(key, JSON.stringify(value));
        return true;
    } catch (error) {
        console.error('ローカルストレージへの保存に失敗:', error);
        return false;
    }
}

/**
 * ローカルストレージからデータを読み込む関数
 * @param {string} key - 読み込むキー
 * @returns {*} - 読み込んだ値（存在しない場合はnull）
 */
function loadFromLocalStorage(key) {
    try {
        const item = localStorage.getItem(key);
        return item ? JSON.parse(item) : null;
    } catch (error) {
        console.error('ローカルストレージからの読み込みに失敗:', error);
        return null;
    }
}

/**
 * 震度を数値から文字列に変換する関数
 * @param {number} shindo - 震度の数値
 * @returns {string} - 震度の文字列表現
 */
function shindoToString(shindo) {
    const shindoMap = {
        10: "1",
        20: "2",
        30: "3",
        40: "4",
        45: "5弱",
        50: "5強",
        55: "6弱",
        60: "6強",
        70: "7"
    };
    return shindoMap[shindo] || "不明";
}

/**
 * マグニチュードの文字列表現を取得する関数
 * @param {number} magnitude - マグニチュード値
 * @returns {string} - マグニチュードの文字列
 */
function magnitudeToString(magnitude) {
    if (magnitude === -1) {
        return "不明";
    }
    return `M${(magnitude / 10).toFixed(1)}`;
}

/**
 * 津波の情報を文字列に変換する関数
 * @param {string} tsunamiType - 津波のタイプ
 * @returns {string} - 津波情報の文字列
 */
function tsunamiToString(tsunamiType) {
    const tsunamiMap = {
        "None": "なし",
        "Unknown": "不明",
        "Checking": "調査中",
        "NonEffective": "若干の海面変動",
        "Watch": "津波注意報",
        "Warning": "津波警報"
    };
    return tsunamiMap[tsunamiType] || "不明";
}

/**
 * エラーメッセージを表示する関数
 * @param {string} message - エラーメッセージ
 */
function showError(message) {
    console.error(message);
    const textarea = document.getElementById('maintextarea');
    if (textarea) {
        textarea.value = `エラー: ${message}`;
    }
}

/**
 * 通知を表示する関数（ブラウザ通知API）
 * @param {string} title - 通知のタイトル
 * @param {string} body - 通知の本文
 */
function showNotification(title, body) {
    if ("Notification" in window && Notification.permission === "granted") {
        new Notification(title, {
            body: body,
            icon: "Img/epicenter.png"
        });
    }
}

/**
 * 通知の許可をリクエストする関数
 */
function requestNotificationPermission() {
    if ("Notification" in window && Notification.permission === "default") {
        Notification.requestPermission().then(permission => {
            if (permission === "granted") {
                console.log("通知が許可されました");
            }
        });
    }
}

// ページ読み込み時に通知の許可をリクエスト
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', requestNotificationPermission);
} else {
    requestNotificationPermission();
}
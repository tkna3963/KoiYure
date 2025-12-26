function NowTimeChange() {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const seconds = String(now.getSeconds()).padStart(2, '0');
    const milli = String(now.getMilliseconds()).padStart(3, '0');
    document.getElementById("now_time").textContent =
        `${year}/${month}/${day} ${hours}:${minutes}:${seconds}(${milli})`;
}

function Menu() {
    // Menu Toggle
    const menu = document.getElementById('menu');
    const toggleMenuBtn = document.getElementById('toggleMenuBtn');
    toggleMenuBtn.addEventListener('click', () => {
        menu.classList.toggle('open');
    });
    // Infomenu Toggle
    const infomenu = document.getElementById('infomenu');
    const toggleinfoMenuBtn = document.getElementById('toggleinfoMenuBtn');
    toggleinfoMenuBtn.addEventListener('click', () => {
        infomenu.classList.toggle('open');
    });
}


function Main() {
    Menu();
    setInterval(NowTimeChange, 1);
}


document.addEventListener("DOMContentLoaded", () => {
    Main();
});
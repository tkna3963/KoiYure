function KoishiFaceUpdate(Url) {
  document.getElementById("koisi").src = Url;
}

function GetJson(url) {
  return fetch(url)
    .then(response => response.json())
    .then(data => {
      return data;
    })
    .catch(error => {
      console.error('Error fetching JSON:', error);
    });
}

// 位置情報を取得する
navigator.geolocation.getCurrentPosition(
  function (position) {
    const latitude = position.coords.latitude;
    const longitude = position.coords.longitude;
    console.log("緯度: " + latitude);
    console.log("経度: " + longitude);
    // Nominatim APIにリクエストを送る
    fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${latitude}&lon=${longitude}`)
      .then(response => response.json())
      .then(data => {
        // data.address に地域情報が入ってる
        console.log("地域情報:", data.address);
        // 例えば市町村だけ欲しい場合
        const place = data.address.city || data.address.town || data.address.village || "不明な地域";
        document.getElementById("NowLocate").textContent = `${place}(${latitude.toFixed(1)},${longitude.toFixed(1)})`;
        if (typeof LeafletMapSet !== 'undefined') {
          L.marker([latitude, longitude])
            .addTo(LeafletMapSet)
            .bindPopup("あなたの現在地:"+ `${place}(${latitude.toFixed(2)},${longitude.toFixed(2)})`)
            .openPopup();
          // 見やすくするためにマップの中心も現在地に
          LeafletMapSet.setView([latitude, longitude], 13);
        }

      })
      .catch(error => {
        console.error("逆ジオコーディングに失敗:", error);
      });

  },
  function (error) {
    switch (error.code) {
      case error.PERMISSION_DENIED:
        console.error("位置情報の取得が許可されていません。");
        break;
      case error.POSITION_UNAVAILABLE:
        console.error("位置情報が利用できません。");
        break;
      case error.TIMEOUT:
        console.error("位置情報の取得がタイムアウトしました。");
        break;
      default:
        console.error("不明なエラーが発生しました。");
        break;
    }
  }
);

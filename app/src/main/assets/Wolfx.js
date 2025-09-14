
function loadJSON(filePath) {
    const xhr = new XMLHttpRequest();
    xhr.open('GET', filePath, false);
    xhr.send();
    if (xhr.status === 200) {
        return JSON.parse(xhr.responseText);
    } else {
        console.error('Error loading JSON:', xhr.status, xhr.statusText);
        return null;
    }
}


// Wolfxデータ変換
function WolxCon(data) {
    if (data.type === "jma_eew") {
        return [
            data.type ?? "",               // 1
            data.Title ?? "",              // 2
            data.CodeType ?? "",           // 3
            data.Issue?.Source ?? "",      // 4
            data.Issue?.Status ?? "",      // 5
            data.EventID ?? "",            // 6
            data.AnnouncedTime ?? "",      // 7
            data.OriginTime ?? "",         // 8
            data.Hypocenter ?? "",         // 9
            data.MaxIntensity ?? "",       // 10
            data.Accuracy?.Epicenter ?? "",// 11
            data.Accuracy?.Depth ?? "",    // 12
            data.Accuracy?.Magnitude ?? "",// 13
            data.MaxIntChange?.String ?? "",// 14
            data.MaxIntChange?.Reason ?? "",// 15
            data.WarnArea?.Chiiki ?? "",   // 16
            data.WarnArea?.Shindo1 ?? "",  // 17
            data.WarnArea?.Shindo2 ?? "",  // 18
            data.WarnArea?.Time ?? "",     // 19
            data.WarnArea?.Type ?? "",     // 20
            data.Serial ?? 0,              // 21
            data.Latitude ?? 0,            // 22
            data.Longitude ?? 0,           // 23
            data.Magunitude ?? 0,          // 24
            data.Depth ?? 0,               // 25
            data.WarnArea?.Arrive ?? false,// 26
            data.isSea ?? false,           // 27
            data.isTraining ?? false,      // 28
            data.isAssumption ?? false,    // 29
            data.isWarn ?? false,          // 30
            data.isFinal ?? false,         // 31
            data.isCancel ?? false,        // 32
            data.OriginalText ?? ""        // 33
        ];
    } else {
        return [
            data.type ?? "",   // 1
            data.id ?? "",     // 2
            data.timestamp ?? "", // 3
            data.ver ?? ""     // 4
        ];
    }
}


function wolfxcoverter(data) {
    // "heartbeat" の場合
    if (data.type === "heartbeat") {
        return `【システムハートビート】\n` +
            `ID: ${data.id}\n` +
            `メッセージ: ${data.message ? data.message : "（なし）"}\n`;
    }

    // "jma_eew" の場合 (緊急地震速報)
    if (data.type === "jma_eew") {
        let message = `【${data.Title}】\n`;
        message += `発表機関: ${data.Issue?.Source} (${data.Issue?.Status})\n`;
        message += `発表ID: ${data.EventID} / 発表回数: 第${data.Serial}報\n`;
        message += `発表時刻: ${data.AnnouncedTime} (JST)\n`;
        message += `地震発生時刻: ${data.OriginTime} (JST)\n`;
        message += `\n震源地: ${data.Hypocenter} (緯度: ${data.Latitude}, 経度: ${data.Longitude})\n`;
        message += `マグニチュード: M${data.Magunitude} 深さ: ${data.Depth}km\n`;
        message += `最大震度: ${data.MaxIntensity}\n`;

        if (data.Accuracy) {
            message += `\n【精度情報】\n`;
            message += `震源位置: ${data.Accuracy?.Epicenter} / 深さ: ${data.Accuracy?.Depth} / マグニチュード: ${data.Accuracy?.Magnitude}\n`;
        }

        if (data.MaxIntChange?.String) {
            message += `最大震度の変更: ${data.MaxIntChange.String} (理由: ${data.MaxIntChange.Reason})\n`;
        }

        // 警報地域情報（震度・発表時刻・種別・到達情報含む）
        if (data.WarnArea && data.WarnArea.length > 0) {
            message += `\n【警報情報】\n`;
            data.WarnArea.forEach(area => {
                message += `地域: ${area.Chiiki}\n`;
                message += `最大震度: ${area.Shindo1}, 最小震度: ${area.Shindo2}\n`;
                message += `発表時刻: ${area.Time}, 種別: ${area.Type}, 地震波到達: ${area.Arrive ? "はい" : "いいえ"}\n`;
                message += `-------------------\n`;
            });
        } else {
            message += `\n【警報情報】\n警報対象地域: なし\n`;
        }

        message += `\n【その他情報】\n`;
        message += `海域の地震: ${data.isSea ? "はい" : "いいえ"}\n`;
        message += `訓練報: ${data.isTraining ? "はい" : "いいえ"}\n`;
        message += `推定震源 (PLUM/レベル/IPF法): ${data.isAssumption ? "はい" : "いいえ"}\n`;
        message += `警報発表: ${data.isWarn ? "はい" : "いいえ"}\n`;
        message += `最終報: ${data.isFinal ? "はい" : "いいえ"}\n`;
        message += `キャンセル報: ${data.isCancel ? "はい" : "いいえ"}\n`;

        if (data.OriginalText) {
            message += `\n【原文】\n${data.OriginalText}\n`;
        }

        return message;
    }

    // 未知のデータタイプの場合
    return `【未対応のデータ】\nタイプ: ${data.type}\n内容:\n${JSON.stringify(data, null, 2)}`;
}

function calculateDistance([lat1, lon1], [lat2, lon2]) {
    const R = 6371; // 地球の半径（km）
    const phi1 = lat1 * Math.PI / 180;
    const phi2 = lat2 * Math.PI / 180;
    const deltaPhi = (lat2 - lat1) * Math.PI / 180;
    const deltaLambda = (lon2 - lon1) * Math.PI / 180;

    const a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
        Math.cos(phi1) * Math.cos(phi2) *
        Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);

    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return R * c; // キロメートル単位
}

function calculateDistanceAttenuation(magJMA, depth, epicenterLocation, pointLocation, amplificationFactor) {
    // マグニチュード (Mw) の計算
    const magW = magJMA - 0.171;
    // 断層長（半径）の計算
    const faultRadius = 10 ** (0.5 * magW - 1.85) / 2;
    // 震央からの距離の計算
    const epicenterDistance = calculateDistance(epicenterLocation, pointLocation);
    // 震源からの距離を計算（最小値3km）
    const hypocenterDistance = Math.max(Math.sqrt(depth ** 2 + epicenterDistance ** 2) - faultRadius, 3);
    // 工学基盤上の最大速度（Vs = 600 m/s）を計算
    const maxSpeed600 = 10 ** (
        0.58 * magW +
        0.0038 * depth - 1.29 -
        Math.log10(hypocenterDistance + 0.0028 * 10 ** (0.5 * magW)) -
        0.002 * hypocenterDistance
    );
    // 基盤の速度（Vs = 400 m/s）の変換
    const maxSpeed400 = maxSpeed600 * 1.31;
    // 増幅率を使って最終的な速度を計算
    const surfaceSpeed = amplificationFactor === 0 ? 0 : maxSpeed400 * (Number(amplificationFactor));
    // 震度を計算
    const intensity = parseFloat((2.68 + 1.72 * Math.log10(surfaceSpeed)).toFixed(2));
    // 結果を返す（震度、震央からの距離、最大速度、増幅率）
    return { intensity, epicenterDistance, surfaceSpeed: parseFloat(surfaceSpeed.toFixed(3)), amplificationFactor };
}

// function WolfxCalcAtte() {
//     if (currentLocation.latitude, currentLocation.longitude, YahooDatas.magnitude) {
//         var SFGIPJ = surfaceGroundInformationProvisionAPI(currentLocation.latitude, currentLocation.longitude)
//         var SFGIPARV = SFGIPJ.features[0].properties.ARV
//         var SFC = calculateDistanceAttenuation(YahooDatas.magnitude, YahooDatas.depth, [YahooDatas.Wave_latitude, YahooDatas.Wave_longitude], [currentLocation.latitude, currentLocation.longitude], SFGIPARV)
//         var SFCI = SFC.intensity
//         var SFCD = Math.round(SFC.epicenterDistance)
//         results_datalist.SFCI = SFCI;
//         results_datalist.SFCD = SFCD;
//         for (const centerP of CentersData.centers) {
//             const AreaSFC = calculateDistanceAttenuation(
//                 YahooDatas.magnitude,
//                 YahooDatas.depth,
//                 [YahooDatas.Wave_latitude, YahooDatas.Wave_longitude],
//                 [centerP.latitude, centerP.longitude],
//                 Number(centerP.properties.arv)
//             );
//             AreaSFClist.push(AreaSFC.intensity);
//             Areanamelist.push(centerP.properties.name);
//             Arvlist.push(Number(centerP.properties.arv));
//             DBLlist.push(AreaSFC.epicenterDistance);
//             centerlalolist.push([centerP.latitude, centerP.longitude])
//         }
//         results_datalist.AreaSFClist = AreaSFClist
//         results_datalist.Areanamelist = Areanamelist
//         results_datalist.Arvlist = Arvlist
//         results_datalist.DBLlist = DBLlist
//         results_datalist.centerlalolist = centerlalolist
//     }
// }



async function PSTable() {
    const response = await fetch("tjma2001h.txt");
    const text = await response.text();

    return text
        .trim()
        .replace(/\r/g, "")
        .replace(/\x20+/g, " ")
        .split("\n")
        .map(line => {
            const s = line.split(" ");
            return {
                p: parseFloat(s[1]),
                s: parseFloat(s[3]),
                depth: parseInt(s[4]),
                distance: parseInt(s[5]),
            };
        });
}

function GetPSValue(table, depth, time) {
    if (depth > 700 || time > 2000) return [NaN, NaN];

    const values = table.filter(x => x.depth === depth);
    if (values.length === 0) return [NaN, NaN];

    let p1 = values.filter(x => x.p <= time).pop();
    let p2 = values.filter(x => x.p >= time)[0];
    if (!p1 || !p2) return [NaN, NaN];
    const p = (time - p1.p) / (p2.p - p1.p) * (p2.distance - p1.distance) + p1.distance;

    let s1 = values.filter(x => x.s <= time).pop();
    let s2 = values.filter(x => x.s >= time)[0];
    if (!s1 || !s2) return [p, NaN];
    const s = (time - s1.s) / (s2.s - s1.s) * (s2.distance - s1.distance) + s1.distance;

    return [p, s];
}

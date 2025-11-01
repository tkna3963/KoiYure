function KoishiFaceUpdate(Url){
    document.getElementById("koisi").src=Url;
}

function GetJson(url){
    return fetch(url)
    .then(response => response.json())
    .then(data => {
        return data;
    })
    .catch(error => {
        console.error('Error fetching JSON:', error);
    });
}
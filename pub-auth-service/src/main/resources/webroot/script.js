function doIdp(idp) {
    const query = replaceInQuery('remember_me', getRememberMeCheckbox());
    window.location.href = "../auth?login_hint=google&" + query;
}

var state;

function setRememberMe(elementId) {
    state = getFromQuery('remember_me')?.toLowerCase() === 'true';
    document.getElementById(elementId).checked = state;
    document.getElementById(elementId + 'Admin').checked = state;
}

function rememberMeToggled() {
    state = !state;
    document.getElementById('checkRememberMe').checked = state;
    document.getElementById('checkRememberMeAdmin').checked = state;
}

function toggleAdmin() {
    const btn = document.getElementById('adminLoginBtn');
    const form = document.getElementById('adminLoginForm');
    if (btn.style.display === 'none') {
        btn.style.display = 'block';
        form.style.display = 'none';
    } else {
        btn.style.display = 'none';
        form.style.display = 'block';
        document.getElementById('user').focus();
    }
}

function replaceInQuery(param, newValue) {
    let query = window.location.search.substring(1);
    const old = getFromQuery(param);
    if (old != null) {
        const entries = query.split('&');
        const newQuery = entries.reduce((prev, curr) => {
            if (curr.startsWith(param + '=')) {
                return prev + '&' + param + '=' + newValue;
            } else {
                return prev + '&' + curr;
            }
        }, '');
        return newQuery.substring(1);
    } else {
        return query + "&" + param + "=" + newValue;
    }
}

function getRememberMeCheckbox() {
    return document.getElementById('checkRememberMe').checked;
}

function getFromQuery(param) {
    let query = window.location.search.substring(1);
    const part = query.split('&').filter(str => str.startsWith(param + '='));
    return part.length > 0 ? (part[0].split('=')[1] || false) : null;
}

function showOnError(elementId, className) {
    const error = getFromQuery('error')
    if (error && error == 401) {
        toggleAdmin();
        const el = document.getElementById(elementId);
        el.classList.replace('d-none', 'd-block');
        const els = document.getElementsByClassName(className);
        Array.prototype.forEach.call(els, e => e.classList.add('is-invalid'));
    }
}

async function postLogin(event) {
    event.preventDefault();
    event.stopPropagation();
    let user = document.getElementById('user').value;
    let encPass = await digestMessage(document.getElementById('pass').value);
    let search = document.location.search.split('&');
    let query = {};
    search.forEach(part => {
        const p = part.split('=');
        query[p[0]] = p[1];
    });

    let {
        redirect_uri,
        state,
        acode,
        client_id,
        code_challenge,
        code_challenge_method,
    } = query;

    let remember_me = getRememberMeCheckbox();

    let body = {
        user: user,
        pass: encPass,
        remember_me: remember_me,
        redirect_uri,
        state,
        acode,
        client_id,
        code_challenge,
        code_challenge_method
    };

    let uri = '../auth/local';
    post(uri, body);

}

function parseBool(val) {
    return val === true || val === "true"
}

/**
 * sends a request to the specified url from a form. this will change the window location.
 * @param {string} path the path to send the post request to
 * @param {object} params the paramiters to add to the url
 * @param {string} [method=post] the method to use on the form
 */

function post(path, params, method = 'post') {

    // The rest of this code assumes you are not using a library.
    // It can be made less wordy if you use one.
    const form = document.createElement('form');
    form.method = method;
    form.action = path;

    for (const key in params) {
        if (params.hasOwnProperty(key)) {
            const hiddenField = document.createElement('input');
            hiddenField.type = 'hidden';
            hiddenField.name = key;
            hiddenField.value = params[key];

            form.appendChild(hiddenField);
        }
    }

    document.body.appendChild(form);
    form.submit();
}

async function digestMessage(message) {
    let seed = Date.now();
    const msgUint8 = new TextEncoder().encode(message + seed);
    const hashBuffer = await crypto.subtle.digest('SHA-256', msgUint8);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
    return hashHex + "-" + seed;
}

function goBack() {
    const uri = getFromQuery('redirect_uri');
    window.location.href = decodeURIComponent(getFromQuery('redirect_uri'));
}

/*
 * Shared by New Job and New Quote -- both reuse the exact same vehicle
 * picker: auto-select a single-vehicle customer's car, tap-to-pick chips
 * for a fleet, free text for walk-ins or new vehicles. Expects these
 * element ids on the page: customerName, customerPhone, vehiclePicker,
 * vehicleDescription, vehiclePlateNumber, vehicleId, vehicle-suggestions.
 */

function fillCustomer(btn) {
    document.getElementById('customerName').value = btn.dataset.name || '';
    document.getElementById('customerPhone').value = btn.dataset.phone || '';
    lookupVehicles(btn.dataset.phone || '');
}

function clearVehicleSelection() {
    document.getElementById('vehicleId').value = '';
    setVehiclePickerSelection(null);
}

function setVehiclePickerSelection(vehicleId) {
    document.querySelectorAll('#vehiclePicker .vehicle-chip').forEach(function (chip) {
        chip.classList.toggle('selected', chip.dataset.id === String(vehicleId));
    });
}

function pickVehicle(id, description) {
    document.getElementById('vehicleId').value = id;
    document.getElementById('vehicleDescription').value = description;
    setVehiclePickerSelection(id);
}

function lookupVehicles(phone) {
    var picker = document.getElementById('vehiclePicker');
    document.getElementById('vehicleId').value = '';
    picker.innerHTML = '';
    picker.style.display = 'none';
    if (!phone) return;

    fetch('/api/vehicles?phone=' + encodeURIComponent(phone))
        .then(function (r) { return r.ok ? r.json() : []; })
        .then(function (vehicles) {
            if (!vehicles.length) return;

            if (vehicles.length === 1) {
                pickVehicle(vehicles[0].id, vehicles[0].description);
                return;
            }

            vehicles.forEach(function (v) {
                var chip = document.createElement('button');
                chip.type = 'button';
                chip.className = 'vehicle-chip';
                chip.textContent = v.plateNumber ? v.description + ' (' + v.plateNumber + ')' : v.description;
                chip.dataset.id = v.id;
                chip.onclick = function () { pickVehicle(v.id, v.description); };
                picker.appendChild(chip);
            });
            picker.style.display = 'flex';
        })
        .catch(function () { /* offline or lookup failed -- free text still works */ });
}

function loadVehicleSuggestions() {
    var datalist = document.getElementById('vehicle-suggestions');
    Promise.all([
        fetch('/vehicle-seed.json').then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; }),
        fetch('/api/vehicles/suggestions').then(function (r) { return r.ok ? r.json() : []; }).catch(function () { return []; })
    ]).then(function (results) {
        var merged = Array.from(new Set(results[0].concat(results[1])));
        merged.forEach(function (description) {
            var option = document.createElement('option');
            option.value = description;
            datalist.appendChild(option);
        });
    });
}

document.addEventListener('DOMContentLoaded', loadVehicleSuggestions);

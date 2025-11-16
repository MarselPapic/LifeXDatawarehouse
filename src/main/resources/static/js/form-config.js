(function(global){
    const pick = (obj, ...keys) => {
        if(!obj) return undefined;
        const entries = Object.keys(obj);
        for(const key of keys){
            if(key == null) continue;
            if(obj[key] !== undefined && obj[key] !== null) return obj[key];
            const match = entries.find(k => k.toLowerCase() === String(key).toLowerCase());
            if(match && obj[match] !== undefined && obj[match] !== null) return obj[match];
        }
        return undefined;
    };

    const formatLabel = (name, value) => {
        if(!value) return '';
        if(!name) return value;
        const short = String(value).split('-')[0];
        return `${name} (${short})`;
    };

    const asyncSources = {
        accounts:{
            url:'/accounts',
            map:item=>{
                const value = pick(item,'accountID','accountId','AccountID');
                const name = pick(item,'accountName','AccountName');
                return value ? {value, label: formatLabel(name,value)} : null;
            }
        },
        addresses:{
            url:'/addresses',
            map:item=>{
                const value = pick(item,'addressID','addressId','AddressID');
                const street = pick(item,'street','Street');
                const city = pick(item,'cityID','CityID');
                const label = street || city ? `${[street,city].filter(Boolean).join(', ')} (${String(value).split('-')[0]})` : value;
                return value ? {value, label, cityID: pick(item,'cityID','CityID')} : null;
            }
        },
        projects:{
            url:accountId=>accountId?`/projects?accountId=${encodeURIComponent(accountId)}`:'/projects',
            map:item=>{
                const value = pick(item,'projectID','projectId','ProjectID');
                const name = pick(item,'projectName','ProjectName');
                const account = pick(item,'accountID','AccountID');
                return value ? {value, label: formatLabel(name,value), accountID: account} : null;
            }
        },
        sites:{
            url:projectId=>projectId?`/sites?projectId=${encodeURIComponent(projectId)}`:'/sites',
            map:item=>{
                const value = pick(item,'siteID','siteId','SiteID');
                const name = pick(item,'siteName','SiteName');
                const project = pick(item,'projectID','ProjectID');
                return value ? {value, label: formatLabel(name,value), projectID: project} : null;
            }
        },
        clients:{
            url:siteId=>siteId?`/clients?siteId=${encodeURIComponent(siteId)}`:'/clients',
            map:item=>{
                const value = pick(item,'clientID','clientId','ClientID');
                const name = pick(item,'clientName','ClientName');
                return value ? {value, label: formatLabel(name,value), siteID: pick(item,'siteID','SiteID')} : null;
            }
        },
        countries:{
            url:'/table/country',
            map:item=>{
                const raw = pick(item,'countryCode','CountryCode','COUNTRYCODE');
                if(!raw) return null;
                const value=String(raw).toUpperCase();
                const name = pick(item,'countryName','CountryName','COUNTRYNAME');
                const label = name ? `${name} (${value})` : value;
                return {value, label};
            }
        },
        cities:{
            url:'/table/city',
            map:item=>{
                const value = pick(item,'cityID','CityID','CITYID');
                if(!value) return null;
                const name = pick(item,'cityName','CityName','CITYNAME');
                const country = pick(item,'countryCode','CountryCode','COUNTRYCODE');
                const countryCode = country ? String(country).toUpperCase() : undefined;
                const labelParts = [name, countryCode].filter(Boolean);
                const short = String(value).split('-')[0];
                const label = labelParts.length>0 ? `${labelParts.join(' • ')} (${short})` : String(value);
                return {value, label, countryCode};
            }
        },
        software:{
            url:'/table/software',
            map:item=>{
                const value = pick(item,'softwareID','SoftwareID','SOFTWAREID');
                if(!value) return null;
                const name = pick(item,'name','Name');
                const release = pick(item,'release','Release');
                const label = [name, release].filter(Boolean).join(' • ');
                return {value, label, displayLabel: label || String(value)};
            }
        },
        deploymentVariants:{
            url:'/deployment-variants',
            map:item=>{
                const value = pick(item,'variantID','variantId','VariantID');
                if(!value) return null;
                const code = pick(item,'variantCode','VariantCode');
                const name = pick(item,'variantName','VariantName');
                const parts = [code, name].filter(Boolean);
                const label = parts.length>0 ? parts.join(' – ') : String(value);
                return {value, label};
            }
        }
    };

    const entityGuidance = {
        Account: {
            summary: 'Create an account to group related projects and contracts for a customer.',
            notes: [
                'The account name should match the official customer name.',
                'Provide contact information when available so downstream teams can reach out quickly.'
            ],
            fieldHints: {
                vat: 'Enter the VAT number exactly as registered by the customer.',
                country: 'Use a two-letter ISO country code if available.'
            }
        },
        Address: {
            summary: 'Capture a postal address that can be reused by projects and sites.',
            notes: ['Select an existing city to ensure geo-information stays consistent.']
        },
        AudioDevice: {
            summary: 'Register a headset, speaker, or microphone used at a client working position.',
            notes: ['Associate the device with the client installation that uses it.']
        },
        City: {
            summary: 'Add a new city so addresses and sites can reference it.',
            notes: ['City IDs must remain unique; use the standardized identifier from the authoritative source.'],
            fieldHints: {
                cityId: 'Follow the naming convention used in the source ERP when creating IDs.'
            }
        },
        Country: {
            summary: 'Maintain the list of supported countries for cities and addresses.',
            notes: ['Country codes must be two-letter ISO codes.']
        },
        DeploymentVariant: {
            summary: 'Describe how LifeX is deployed for a project (variant, name, and activation).',
            notes: ['Inactive variants should only be used for historical records.']
        },
        PhoneIntegration: {
            summary: 'Document the phone integration that connects a client to telephony services.',
            notes: ['Capture firmware and serial numbers when needed for troubleshooting.']
        },
        Project: {
            summary: 'Create a project that ties an account to a deployment variant and address.',
            notes: ['A project must belong to an account and typically references the primary installation address.'],
            fieldHints: {
                accId: 'Selecting an account filters the available projects in dependent forms.',
                addrId: 'Choose an address that represents the project’s main location.'
            }
        },
        Radio: {
            summary: 'Track radios installed at a site for emergency communication.',
            notes: ['Link radios to the client they are assigned to when applicable.']
        },
        Server: {
            summary: 'Register a server at a site, including platform and availability attributes.',
            notes: ['Specify whether the server is virtualized to support capacity planning.']
        },
        ServiceContract: {
            summary: 'Define the service contract that governs project support.',
            notes: ['Contracts cascade down from account to project to site. Select them in that order.'],
            fieldHints: {
                contractNumber: 'Use the reference number agreed with the customer or internal billing ID.'
            }
        },
        Site: {
            summary: 'Create a site to represent the physical installation for a project.',
            notes: [
                'Make sure the project exists before adding its site.',
                'Capture each deployed software package with status and lifecycle dates so the inventory stays current.'
            ],
            fieldHints: {
                addrId: 'Address selection determines where the installation is located.',
                softwareInstallations: 'Add every software deployment along with its status and the relevant dates.'
            }
        },
        Software: {
            summary: 'Catalog a software release so it can be linked to deployments and upgrade plans.',
            notes: [
                'Provide support window information for upgrade planning where possible.',
                'Flag third-party software so rollout plans can account for external vendors.'
            ]
        },
        UpgradePlan: {
            summary: 'Plan a software upgrade by selecting the site and software along with a target window.',
            notes: ['Ensure the planned window does not overlap with maintenance freezes.']
        },
        WorkingPosition: {
            summary: 'Create a client working position that will host hardware and software installations.',
            notes: ['Working positions should reference the site where the client is physically installed.']
        }
    };

    const entityFields = {
        Account: [
            { id: 'name', label: 'Name', component: 'input', name: 'AccountName' },
            { id: 'contact', label: 'Contact Person', component: 'input', name: 'ContactName', required: false, hint: 'Optional contact person for the customer account.' },
            { id: 'email', label: 'Email', component: 'input', name: 'ContactEmail', required: false, inputType: 'email', autocomplete: 'email', inputmode: 'email', hint: 'Used for automated notifications.' },
            { id: 'phone', label: 'Phone', component: 'input', name: 'ContactPhone', required: false, inputType: 'tel', pattern: '^[+0-9()\s-]{5,}$', inputmode: 'tel', hint: 'Include the country code if possible.' },
            { id: 'vat', label: 'VAT ID', component: 'input', name: 'VatNumber', required: false, hint: 'Enter the tax number without spaces.' },
            { id: 'country', label: 'Country', component: 'input', name: 'Country', required: false, hint: 'Two-letter ISO code, e.g. DE or US.' }
        ],
        Address: [
            { id: 'street', label: 'Street', component: 'input', name: 'Street', hint: 'Include house number if available.' },
            { id: 'cityID', label: 'Select city', component: 'asyncSelect', source: 'cities', allowManual: false, placeholder: 'Select city', name: 'CityID', hint: 'Only existing cities can be linked to an address.' }
        ],
        AudioDevice: [
            { id: 'client', label: 'Select client', component: 'asyncSelect', source: 'clients', placeholder: 'Select client', allowManual: false, name: 'ClientID', hint: 'Choose the working position that uses this device.' },
            { id: 'brand', label: 'Brand', component: 'input', name: 'AudioDeviceBrand', required: false },
            { id: 'serial', label: 'Serial Number', component: 'input', name: 'DeviceSerialNr', required: false },
            { id: 'fw', label: 'Firmware', component: 'input', name: 'AudioDeviceFirmware', required: false },
            { id: 'dtype', label: 'DeviceType', component: 'select', options: ['HEADSET','SPEAKER','MIC'], name: 'DeviceType' }
        ],
        City: [
            { id: 'cityId', label: 'CityID', component: 'input', dataset: { uppercase: true }, name: 'CityID', hint: 'Use the agreed city identifier (uppercase).' },
            { id: 'cityName', label: 'City Name', component: 'input', name: 'CityName' },
            { id: 'countryCode', label: 'Select country', component: 'asyncSelect', source: 'countries', allowManual: false, placeholder: 'Select country', name: 'CountryCode' }
        ],
        Country: [
            { id: 'countryCode', label: 'Country Code', component: 'input', pattern: '[A-Za-z]{2}', maxlength: 2, dataset: { uppercase: true }, autocomplete: 'off', name: 'CountryCode' },
            { id: 'countryName', label: 'Country Name', component: 'input', name: 'CountryName' }
        ],
        DeploymentVariant: [
            { id: 'variantCode', label: 'VariantCode', component: 'input', name: 'VariantCode' },
            { id: 'variantName', label: 'VariantName', component: 'input', name: 'VariantName' },
            { id: 'description', label: 'Description', component: 'input', name: 'Description', required: false },
            { id: 'active', label: 'IsActive', component: 'select', options: ['true','false'], name: 'IsActive', valueType: 'boolean' }
        ],
        PhoneIntegration: [
            { id: 'client', label: 'Select client', component: 'asyncSelect', source: 'clients', placeholder: 'Select client', allowManual: false, name: 'ClientID', hint: 'Integrations attach to the working position using the phone.' },
            { id: 'type', label: 'PhoneType', component: 'select', options: ['Emergency','NonEmergency','Both'], name: 'PhoneType' },
            { id: 'brand', label: 'Brand', component: 'input', name: 'PhoneBrand', required: false },
            { id: 'serial', label: 'Serial Number', component: 'input', name: 'PhoneSerialNr', required: false },
            { id: 'fw', label: 'Firmware', component: 'input', name: 'PhoneFirmware', required: false }
        ],
        Project: [
            { id: 'sap', label: 'SAP ID', component: 'input', name: 'ProjectSAPID', required: false, hint: 'Optional internal reference (from SAP).' },
            { id: 'pname', label: 'Project Name', component: 'input', name: 'ProjectName' },
            { id: 'variantId', label: 'Select deployment variant', component: 'asyncSelect', source: 'deploymentVariants', placeholder: 'Select deployment variant', allowManual: false, name: 'DeploymentVariantID', hint: 'Determines the LifeX deployment flavor.' },
            { id: 'bundle', label: 'Bundle Type', component: 'input', name: 'BundleType', required: false },
            { id: 'lifecycle', label: 'Lifecycle Status', component: 'select', name: 'LifecycleStatus', options: [
                { value: 'PLANNED', label: 'Planned' },
                { value: 'ACTIVE', label: 'Active' },
                { value: 'MAINTENANCE', label: 'Maintenance' },
                { value: 'RETIRED', label: 'Retired' }
            ] },
            { id: 'accId', label: 'Select account', component: 'asyncSelect', source: 'accounts', allowManual: false, name: 'AccountID' },
            { id: 'addrId', label: 'Select address', component: 'asyncSelect', source: 'addresses', allowManual: false, placeholder: 'Select address', name: 'AddressID' }
        ],
        Radio: [
            { id: 'siteId', label: 'Select site', component: 'asyncSelect', source: 'sites', allowManual: false, name: 'SiteID', hint: 'Radios are installed at a specific site.' },
            { id: 'brand', label: 'Brand', component: 'input', name: 'RadioBrand', required: false },
            { id: 'serial', label: 'Serial Number', component: 'input', name: 'RadioSerialNr', required: false },
            { id: 'mode', label: 'Mode', component: 'select', options: ['Analog','Digital'], name: 'Mode' },
            { id: 'standard', label: 'Digital Standard', component: 'select', options: ['Airbus','Motorola','ESN','P25','Polycom','Teltronics'], name: 'DigitalStandard', required: false },
            { id: 'client', label: 'AssignedClientID (UUID)', component: 'asyncSelect', source: 'clients', allowManual: false, name: 'AssignedClientID', required: false, placeholder: 'Select client (optional)', dependsOn: 'siteId' }
        ],
        Server: [
            { id: 'siteId', label: 'Select site', component: 'asyncSelect', source: 'sites', allowManual: false, name: 'SiteID', hint: 'Servers must be tied to the site where they are deployed.' },
            { id: 'name', label: 'Server Name', component: 'input', name: 'ServerName' },
            { id: 'brand', label: 'Brand', component: 'input', name: 'ServerBrand', required: false },
            { id: 'serial', label: 'Serial Number', component: 'input', name: 'ServerSerialNr', required: false },
            { id: 'os', label: 'Operating System', component: 'input', name: 'ServerOS', required: false },
            { id: 'patch', label: 'Patch Level', component: 'input', name: 'PatchLevel', required: false },
            { id: 'vplat', label: 'Virtual Platform', component: 'select', options: ['BareMetal','HyperV','vSphere'], name: 'VirtualPlatform' },
            { id: 'vver', label: 'Virtual Version', component: 'input', name: 'VirtualVersion', required: false },
            { id: 'ha', label: 'HighAvailability', component: 'select', options: ['true','false'], name: 'HighAvailability', valueType: 'boolean' }
        ],
        ServiceContract: [
            { id: 'accountID', label: 'Select account', component: 'asyncSelect', source: 'accounts', placeholder: 'Select account', allowManual: false, name: 'AccountID', hint: 'Contracts start at the account level.' },
            { id: 'projectID', label: 'Select project', component: 'asyncSelect', source: 'projects', placeholder: 'Select project', allowManual: false, dependsOn: 'accountID', name: 'ProjectID', hint: 'Projects list is filtered by the selected account.' },
            { id: 'siteID', label: 'Select site', component: 'asyncSelect', source: 'sites', placeholder: 'Select site', allowManual: false, dependsOn: 'projectID', emptyText: 'No sites available for selection', name: 'SiteID', hint: 'Sites list updates once a project is selected.' },
            { id: 'contractNumber', label: 'Contract Number', component: 'input', name: 'ContractNumber', hint: 'Reference number from the signed agreement.' },
            { id: 'status', label: 'Status', component: 'select', options: ['Planned','Approved','InProgress','Done','Canceled'], name: 'Status' },
            { id: 'startDate', label: 'Start Date', component: 'input', inputType: 'date', name: 'StartDate' },
            { id: 'endDate', label: 'End Date', component: 'input', inputType: 'date', name: 'EndDate', hint: 'End date may remain empty for ongoing contracts.' }
        ],
        Site: [
            { id: 'pId', label: 'Select project', component: 'asyncSelect', source: 'projects', allowManual: false, name: 'ProjectID', hint: 'Each site must belong to an existing project.' },
            { id: 'name', label: 'Site Name', component: 'input', name: 'SiteName' },
            { id: 'addrId', label: 'Select address', component: 'asyncSelect', source: 'addresses', allowManual: false, placeholder: 'Select address', name: 'AddressID', hint: 'Choose the physical location for this site.' },
            { id: 'zone', label: 'FireZone', component: 'input', name: 'FireZone', required: false },
            { id: 'tenant', label: 'TenantCount', component: 'input', inputType: 'number', min: '0', step: '1', required: false, name: 'TenantCount' },
            { id: 'softwareInstallations', label: 'Software installations', component: 'softwareList', required: false, hint: 'Add deployed software packages with their status and key dates.', addLabel: 'Add software entry', emptyLabel: 'No software entries added yet.' }
        ],
        Software: [
            { id: 'swName', label: 'Name', component: 'input', name: 'Name' },
            { id: 'swRelease', label: 'Release', component: 'input', name: 'Release' },
            { id: 'swRevision', label: 'Revision', component: 'input', name: 'Revision' },
            { id: 'swPhase', label: 'SupportPhase', component: 'select', options: ['Preview','Production','EoL'], name: 'SupportPhase' },
            { id: 'swLicense', label: 'License Model', component: 'input', name: 'LicenseModel', required: false },
            { id: 'swThirdParty', label: 'Third-party vendor', component: 'select',
              options: [
                  { value: 'false', label: 'LifeX / first-party' },
                  { value: 'true', label: 'External third-party' }
              ],
              name: 'ThirdParty', placeholder: 'Select vendor type' },
            { id: 'swEos', label: 'End of Sales', component: 'input', inputType: 'date', name: 'EndOfSalesDate', required: false },
            { id: 'swSupportStart', label: 'Support Start', component: 'input', inputType: 'date', name: 'SupportStartDate', required: false },
            { id: 'swSupportEnd', label: 'Support End', component: 'input', inputType: 'date', name: 'SupportEndDate', required: false }
        ],
        UpgradePlan: [
            { id: 'siteID', label: 'Select site', component: 'asyncSelect', source: 'sites', placeholder: 'Select site', allowManual: false, name: 'SiteID', hint: 'Upgrades are planned for a specific installation site.' },
            { id: 'softwareID', label: 'Select software', component: 'asyncSelect', source: 'software', placeholder: 'Select software', allowManual: false, name: 'SoftwareID', hint: 'Pick the target software release for the upgrade.' },
            { id: 'plannedStart', label: 'Planned Start', component: 'input', inputType: 'date', name: 'PlannedWindowStart' },
            { id: 'plannedEnd', label: 'Planned End', component: 'input', inputType: 'date', name: 'PlannedWindowEnd' },
            { id: 'status', label: 'Status', component: 'select', options: ['Planned','Approved','InProgress','Done','Canceled'], name: 'Status' },
            { id: 'createdAt', label: 'Created On', component: 'input', inputType: 'date', name: 'CreatedAt', defaultValue: () => new Date().toISOString().split('T')[0] },
            { id: 'createdBy', label: 'Created By', component: 'input', name: 'CreatedBy' }
        ],
        WorkingPosition: [
            { id: 'siteId', label: 'Select site', component: 'asyncSelect', source: 'sites', allowManual: false, name: 'SiteID', hint: 'Clients are always assigned to a site.' },
            { id: 'name', label: 'Client Name', component: 'input', name: 'ClientName', hint: 'Pick a name that matches the device label in the field.' },
            { id: 'brand', label: 'Brand', component: 'input', name: 'ClientBrand', required: false },
            { id: 'serial', label: 'Serial Number', component: 'input', name: 'ClientSerialNr', required: false },
            { id: 'os', label: 'OS', component: 'input', name: 'ClientOS', required: false },
            { id: 'patch', label: 'Patch Level', component: 'input', name: 'PatchLevel', required: false },
            { id: 'install', label: 'InstallType', component: 'select', options: ['LOCAL','BROWSER'], name: 'InstallType' }
        ]
    };

    global.FormConfig = {
        asyncSources,
        entityGuidance,
        entityFields,
        getFields(entity){
            return entityFields[entity] || [];
        },
        getGuidance(entity){
            return entityGuidance[entity];
        },
        getMessage(entity){
            const guidance = entityGuidance[entity];
            return guidance ? guidance.summary : undefined;
        }
    };
})(window);

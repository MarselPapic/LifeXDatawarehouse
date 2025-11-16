(function(global){
    const registryTarget = global || {};

    function normalizeTypeKey(value){
        if(value === undefined || value === null) return '';
        return String(value).trim().toLowerCase().replace(/[^a-z0-9]/g, '');
    }

    const ENTITY_TYPE_MAP = {
        account: { detailType: 'account', typeToken: 'type:account', table: 'Account', detailTable: 'Account', aliases: ['account', 'accounts'] },
        project: { detailType: 'project', typeToken: 'type:project', table: 'Project', detailTable: 'Project', aliases: ['project', 'projects'] },
        site:    { detailType: 'site',    typeToken: 'type:site',    table: 'Site',    detailTable: 'Site',    aliases: ['site', 'sites'] },
        server:  { detailType: 'server',  typeToken: 'type:server',  table: 'Server',  detailTable: 'Server',  aliases: ['server', 'servers'] },
        client:  { detailType: 'client',  typeToken: 'type:client',  table: 'WorkingPosition', detailTable: 'Clients', aliases: ['client', 'clients', 'workingposition', 'workingpositions'] },
        radio:   { detailType: 'radio',   typeToken: 'type:radio',   table: 'Radio',   detailTable: 'Radio',   aliases: ['radio', 'radios'] },
        audio:   { detailType: 'audio',   typeToken: 'type:audio',   table: 'AudioDevice', detailTable: 'AudioDevice', aliases: ['audio', 'audiodevice', 'audiodevices', 'audioDevice', 'audioDevices'] },
        phone:   { detailType: 'phone',   typeToken: 'type:phone',   table: 'PhoneIntegration', detailTable: 'PhoneIntegration', aliases: ['phone', 'phoneintegration', 'phoneintegrations', 'phoneIntegration', 'phoneIntegrations'] },
        country: { detailType: 'country', typeToken: 'type:country', table: 'Country', detailTable: 'Country', aliases: ['country', 'countries'] },
        city:    { detailType: 'city',    typeToken: 'type:city',    table: 'City',    detailTable: 'City',    aliases: ['city', 'cities'] },
        address: { detailType: 'address', typeToken: 'type:address', table: 'Address', detailTable: 'Address', aliases: ['address', 'addresses'] },
        deploymentvariant: {
            detailType: 'deploymentvariant',
            typeToken: 'type:deploymentvariant',
            table: 'DeploymentVariant',
            detailTable: 'DeploymentVariant',
            aliases: ['deploymentvariant', 'variant', 'deploymentvariants', 'variants']
        },
        software: {
            detailType: 'software',
            typeToken: 'type:software',
            table: 'Software',
            detailTable: 'Software',
            aliases: ['software', 'softwares']
        },
        upgradeplan: {
            detailType: 'upgradeplan',
            typeToken: 'type:upgradeplan',
            table: 'UpgradePlan',
            detailTable: 'UpgradePlan',
            aliases: ['upgradeplan', 'upgradeplans']
        },
        servicecontract: {
            detailType: 'servicecontract',
            typeToken: 'type:servicecontract',
            table: 'ServiceContract',
            detailTable: 'ServiceContract',
            aliases: ['servicecontract', 'servicecontracts', 'contract', 'contracts']
        }
    };

    const TABLE_NAME_LOOKUP = {};
    Object.entries(ENTITY_TYPE_MAP).forEach(([key, info]) => {
        const aliases = new Set([key, info.table, info.detailType, info.detailTable, ...(info.aliases || [])]);
        aliases.forEach(alias => {
            const normalized = normalizeTypeKey(alias);
            if(normalized){
                TABLE_NAME_LOOKUP[normalized] = info;
            }
        });
    });

    function canonicalTableForType(value){
        const normalized = normalizeTypeKey(value);
        if(!normalized) return value;
        const info = TABLE_NAME_LOOKUP[normalized];
        if(info && info.detailTable){
            return info.detailTable;
        }
        return value;
    }

    function canonicalConfigKeyForTable(table){
        const canonical = canonicalTableForType(table);
        if(!canonical) return null;
        if(canonical === 'Clients') return 'WorkingPosition';
        return canonical;
    }

    registryTarget.EntityTypeRegistry = {
        normalizeTypeKey,
        ENTITY_TYPE_MAP,
        TABLE_NAME_LOOKUP,
        canonicalTableForType,
        canonicalConfigKeyForTable
    };
})(typeof window !== 'undefined' ? window : this);

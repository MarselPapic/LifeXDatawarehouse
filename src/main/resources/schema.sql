-- =========================================================
-- H2 DDL for the LifeX inventory/projects database (normalized)
-- =========================================================

-- ---------- Safety check: drop tables in dependency order ----------
DROP TABLE IF EXISTS ServiceContract;
DROP TABLE IF EXISTS UpgradePlan;
DROP TABLE IF EXISTS InstalledSoftware;
DROP TABLE IF EXISTS PhoneIntegration;
DROP TABLE IF EXISTS AudioDevice;
DROP TABLE IF EXISTS Radio;
DROP TABLE IF EXISTS Clients;
DROP TABLE IF EXISTS Server;
DROP TABLE IF EXISTS Site;
DROP TABLE IF EXISTS Project;
DROP TABLE IF EXISTS Software;
DROP TABLE IF EXISTS DeploymentVariant;
DROP TABLE IF EXISTS Account;
DROP TABLE IF EXISTS Address;
DROP TABLE IF EXISTS City;
DROP TABLE IF EXISTS Country;

-- =========================================================
-- 11.1 Country table
-- =========================================================
CREATE TABLE Country (
                         CountryCode   VARCHAR(2)   PRIMARY KEY,         -- ISO 3166-1 alpha-2 code
                         CountryName   VARCHAR(100) NOT NULL
);

-- =========================================================
-- 10.1 City table
-- =========================================================
CREATE TABLE City (
                      CityID        VARCHAR(50)  PRIMARY KEY,         -- natural identifier
                      CityName      VARCHAR(100) NOT NULL,
                      CountryCode   VARCHAR(2)   NOT NULL,
                      CONSTRAINT fk_city_country
                          FOREIGN KEY (CountryCode) REFERENCES Country(CountryCode)
);

-- =========================================================
-- 9.1 Address table
-- =========================================================
CREATE TABLE Address (
                         AddressID     UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                         Street        VARCHAR(150) NOT NULL,
                         CityID        VARCHAR(50)  NOT NULL,
                         CONSTRAINT fk_address_city
                             FOREIGN KEY (CityID) REFERENCES City(CityID)
);

-- =========================================================
-- 8.1 Account table
-- =========================================================
CREATE TABLE Account (
                         AccountID     UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                         AccountName   VARCHAR(150) NOT NULL,
                         ContactName   VARCHAR(100),
                         ContactEmail  VARCHAR(100),
                         ContactPhone  VARCHAR(30),
                         VATNumber     VARCHAR(30),
                         Country       VARCHAR(50)
);

-- =========================================================
-- 7.1 DeploymentVariant table
-- =========================================================
CREATE TABLE DeploymentVariant (
                                   VariantID     UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                                   VariantCode   VARCHAR(150) NOT NULL,
                                   VariantName   VARCHAR(100) NOT NULL,
                                   Description   VARCHAR(100),
                                   IsActive      BOOLEAN NOT NULL,
                                   CONSTRAINT uq_deploy_variant_code UNIQUE (VariantCode),
                                   CONSTRAINT uq_deploy_variant_name UNIQUE (VariantName)
);

-- =========================================================
-- 6.1 Project table
-- =========================================================
CREATE TABLE Project (
                         ProjectID           UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                         ProjectSAPID        VARCHAR(50),
                         ProjectName         VARCHAR(100) NOT NULL,
                         DeploymentVariantID UUID NOT NULL,
                         BundleType          VARCHAR(50),
                         CreateDateTime      DATE,
                         LifecycleStatus     VARCHAR(20) NOT NULL,
                         AccountID           UUID NOT NULL,
                         AddressID           UUID NOT NULL,
                         CONSTRAINT uq_project_sap UNIQUE (ProjectSAPID),
                         CONSTRAINT fk_project_variant FOREIGN KEY (DeploymentVariantID)
                             REFERENCES DeploymentVariant(VariantID),
                         CONSTRAINT fk_project_account FOREIGN KEY (AccountID)
                             REFERENCES Account(AccountID),
                         CONSTRAINT fk_project_address FOREIGN KEY (AddressID)
                             REFERENCES Address(AddressID)
);

-- =========================================================
-- 12.1 Site table
-- =========================================================
CREATE TABLE Site (
                      SiteID       UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                      SiteName     VARCHAR(100) NOT NULL,
                      ProjectID    UUID NOT NULL,
                      AddressID    UUID NOT NULL,
                      FireZone     VARCHAR(50),
                      TenantCount  INT,
                      CONSTRAINT fk_site_project FOREIGN KEY (ProjectID)
                          REFERENCES Project(ProjectID),
                      CONSTRAINT fk_site_address FOREIGN KEY (AddressID)
                          REFERENCES Address(AddressID)
);

-- =========================================================
-- 18.1 Software table
-- =========================================================
CREATE TABLE Software (
                          SoftwareID       UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                          Name             VARCHAR(100) NOT NULL,
                          Release          VARCHAR(20)  NOT NULL,
                          Revision         VARCHAR(20)  NOT NULL,
                          SupportPhase     VARCHAR(10)  NOT NULL,
                          LicenseModel     VARCHAR(50),
                          ThirdParty       BOOLEAN      NOT NULL DEFAULT FALSE,
                          EndOfSalesDate   DATE,
                          SupportStartDate DATE,
                          SupportEndDate   DATE,
                          CONSTRAINT ck_software_supportphase
                              CHECK (SupportPhase IN ('Preview','Production','EoL'))
);

-- =========================================================
-- 13.1 Server table
-- =========================================================
CREATE TABLE Server (
                        ServerID         UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                        SiteID           UUID NOT NULL,
                        ServerName       VARCHAR(100) NOT NULL,
                        ServerBrand      VARCHAR(50),
                        ServerSerialNr   VARCHAR(100),
                        ServerOS         VARCHAR(100),
                        PatchLevel       VARCHAR(50),
                        VirtualPlatform  VARCHAR(20),
                        VirtualVersion   VARCHAR(50),
                        HighAvailability BOOLEAN NOT NULL,
                        CONSTRAINT fk_server_site FOREIGN KEY (SiteID)
                            REFERENCES Site(SiteID),
                        CONSTRAINT ck_server_virtualplatform
                            CHECK (VirtualPlatform IN ('BareMetal','HyperV','vSphere') OR VirtualPlatform IS NULL)
);

-- =========================================================
-- 14.1 Clients table
-- =========================================================
CREATE TABLE Clients (
                         ClientID       UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                         SiteID         UUID NOT NULL,
                         ClientName     VARCHAR(100) NOT NULL,
                         ClientBrand    VARCHAR(50),
                         ClientSerialNr VARCHAR(100),
                         ClientOS       VARCHAR(100),
                         PatchLevel     VARCHAR(50),
                         InstallType    VARCHAR(20) NOT NULL,
                         CONSTRAINT fk_clients_site FOREIGN KEY (SiteID)
                             REFERENCES Site(SiteID),
                         CONSTRAINT ck_clients_installtype
                             CHECK (InstallType IN ('LOCAL','BROWSER'))
);

-- =========================================================
-- 15.1 Radio table
-- =========================================================
CREATE TABLE Radio (
                       RadioID          UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                       SiteID           UUID NOT NULL,
                       AssignedClientID UUID,
                       RadioBrand       VARCHAR(50),
                       RadioSerialNr    VARCHAR(100),
                       Mode             VARCHAR(10) NOT NULL,
                       DigitalStandard  VARCHAR(20),
                       CONSTRAINT fk_radio_site FOREIGN KEY (SiteID)
                           REFERENCES Site(SiteID),
                       CONSTRAINT fk_radio_client FOREIGN KEY (AssignedClientID)
                           REFERENCES Clients(ClientID),
                       CONSTRAINT ck_radio_mode
                           CHECK (Mode IN ('Analog','Digital')),
                       CONSTRAINT ck_radio_digitalstandard
                           CHECK (DigitalStandard IS NULL OR DigitalStandard IN ('Airbus','Motorola','ESN','P25','Polycom','Teltronics','Tetra'))
);

-- =========================================================
-- 16.1 AudioDevice table
-- =========================================================
CREATE TABLE AudioDevice (
                             AudioDeviceID       UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                             ClientID            UUID NOT NULL,
                             AudioDeviceBrand    VARCHAR(50),
                             DeviceSerialNr      VARCHAR(100),
                             AudioDeviceFirmware VARCHAR(50),
                             DeviceType          VARCHAR(10) NOT NULL,
                             CONSTRAINT fk_audiodevice_client FOREIGN KEY (ClientID)
                                 REFERENCES Clients(ClientID),
                             CONSTRAINT ck_audiodevice_devicetype
                                 CHECK (DeviceType IN ('HEADSET','SPEAKER','MIC'))
);

-- =========================================================
-- 17.1 PhoneIntegration table
-- =========================================================
CREATE TABLE PhoneIntegration (
                                  PhoneIntegrationID  UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                                  ClientID            UUID NOT NULL,
                                  PhoneType           VARCHAR(20) NOT NULL,
                                  PhoneBrand          VARCHAR(50),
                                  PhoneSerialNr       VARCHAR(100),
                                  PhoneFirmware       VARCHAR(50),
                                  CONSTRAINT fk_phone_client FOREIGN KEY (ClientID)
                                      REFERENCES Clients(ClientID),
                                  CONSTRAINT ck_phone_type
                                      CHECK (PhoneType IN ('Emergency','NonEmergency','Both'))
);

-- =========================================================
-- 19.1 InstalledSoftware table
-- =========================================================
CREATE TABLE InstalledSoftware (
                                   InstalledSoftwareID UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                                   SiteID              UUID NOT NULL,
                                   SoftwareID          UUID NOT NULL,
                                   Status              VARCHAR(12) NOT NULL DEFAULT 'Offered',
                                   OfferedDate         DATE,
                                   InstalledDate       DATE,
                                   RejectedDate        DATE,
                                   OutdatedDate        DATE,
                                   CONSTRAINT fk_instsw_site FOREIGN KEY (SiteID)
                                       REFERENCES Site(SiteID),
                                   CONSTRAINT fk_instsw_software FOREIGN KEY (SoftwareID)
                                       REFERENCES Software(SoftwareID),
                                   CONSTRAINT ck_instsw_status CHECK (Status IN ('Offered','Installed','Rejected','Outdated'))
);

-- =========================================================
-- 20.1 UpgradePlan table
-- =========================================================
CREATE TABLE UpgradePlan (
                             UpgradePlanID      UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                             SiteID             UUID NOT NULL,
                             SoftwareID         UUID NOT NULL,
                             PlannedWindowStart DATE NOT NULL,
                             PlannedWindowEnd   DATE NOT NULL,
                             Status             VARCHAR(12) NOT NULL,
                             CreatedAt          DATE NOT NULL,
                             CreatedBy          VARCHAR(20) NOT NULL,
                             CONSTRAINT fk_upgrade_site FOREIGN KEY (SiteID)
                                 REFERENCES Site(SiteID),
                             CONSTRAINT fk_upgrade_software FOREIGN KEY (SoftwareID)
                                 REFERENCES Software(SoftwareID),
                             CONSTRAINT ck_upgrade_status
                                 CHECK (Status IN ('Planned','Approved','InProgress','Done','Canceled'))
);

-- =========================================================
-- 21.1 ServiceContract table
-- =========================================================
CREATE TABLE ServiceContract (
                                 ContractID     UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                                 AccountID      UUID NOT NULL,
                                 ProjectID      UUID NOT NULL,
                                 SiteID         UUID NOT NULL,
                                 ContractNumber VARCHAR(50) NOT NULL,
                                 Status         VARCHAR(12) NOT NULL,
                                 StartDate      DATE NOT NULL,
                                 EndDate        DATE NOT NULL,
                                 CONSTRAINT fk_contract_account FOREIGN KEY (AccountID)
                                     REFERENCES Account(AccountID),
                                 CONSTRAINT fk_contract_project FOREIGN KEY (ProjectID)
                                     REFERENCES Project(ProjectID),
                                 CONSTRAINT fk_contract_site FOREIGN KEY (SiteID)
                                     REFERENCES Site(SiteID),
                                 CONSTRAINT ck_contract_status
                                     CHECK (Status IN ('Planned','Approved','InProgress','Done','Canceled'))
);

-- =========================================================
-- Helpful indexes for query performance
-- =========================================================
CREATE INDEX ix_project_account      ON Project(AccountID);
CREATE INDEX ix_site_project         ON Site(ProjectID);
CREATE INDEX ix_clients_site         ON Clients(SiteID);
CREATE INDEX ix_server_site          ON Server(SiteID);
CREATE INDEX ix_radio_site           ON Radio(SiteID);
CREATE INDEX ix_audiodevice_client   ON AudioDevice(ClientID);
CREATE INDEX ix_phone_client         ON PhoneIntegration(ClientID);
CREATE INDEX ix_installed_site       ON InstalledSoftware(SiteID);
CREATE INDEX ix_installed_software   ON InstalledSoftware(SoftwareID);
CREATE INDEX ix_upgrade_site         ON UpgradePlan(SiteID);
CREATE INDEX ix_upgrade_software     ON UpgradePlan(SoftwareID);
CREATE INDEX ix_service_contracts    ON ServiceContract(AccountID, ProjectID, SiteID);

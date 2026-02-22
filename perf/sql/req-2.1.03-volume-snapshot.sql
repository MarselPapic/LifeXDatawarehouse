-- Requirement 2.1.03 volume snapshot
-- Run in H2 console (or any SQL client against the same DB instance).
-- This script captures current data volume so you can attach evidence to the performance report.

-- Global entity counts
SELECT 'customers (accounts)' AS metric, COUNT(*) AS actual_count FROM Account;
SELECT 'projects (total)' AS metric, COUNT(*) AS actual_count FROM Project;
SELECT 'sites (total)' AS metric, COUNT(*) AS actual_count FROM Site;
SELECT 'servers (total)' AS metric, COUNT(*) AS actual_count FROM Server;
SELECT 'clients / working positions (total)' AS metric, COUNT(*) AS actual_count FROM Clients;
SELECT 'audio devices (total)' AS metric, COUNT(*) AS actual_count FROM AudioDevice;
SELECT 'phone integrations (total)' AS metric, COUNT(*) AS actual_count FROM PhoneIntegration;
SELECT 'installed software (total)' AS metric, COUNT(*) AS actual_count FROM InstalledSoftware;
SELECT 'service contracts (total)' AS metric, COUNT(*) AS actual_count FROM ServiceContract;

-- Per-project distributions (for requirement interpretation)
SELECT 'sites per project (max)' AS metric, COALESCE(MAX(cnt), 0) AS actual_count
FROM (
    SELECT ProjectID, COUNT(*) AS cnt
    FROM Site
    GROUP BY ProjectID
) t;

SELECT 'service contracts per project (max)' AS metric, COALESCE(MAX(cnt), 0) AS actual_count
FROM (
    SELECT ProjectID, COUNT(*) AS cnt
    FROM ServiceContract
    GROUP BY ProjectID
) t;

-- Approximation for "individual SW & HW components per offer and delivery":
-- interpreted as deployed components per project using available schema entities.
SELECT 'components per project (max, installed SW + HW rows)' AS metric, COALESCE(MAX(component_count), 0) AS actual_count
FROM (
    SELECT p.ProjectID,
           COUNT(DISTINCT isw.InstalledSoftwareID)
         + COUNT(DISTINCT srv.ServerID)
         + COUNT(DISTINCT cli.ClientID)
         + COUNT(DISTINCT rad.RadioID)
         + COUNT(DISTINCT aud.AudioDeviceID)
         + COUNT(DISTINCT ph.PhoneIntegrationID) AS component_count
    FROM Project p
    LEFT JOIN Site s ON s.ProjectID = p.ProjectID
    LEFT JOIN InstalledSoftware isw ON isw.SiteID = s.SiteID
    LEFT JOIN Server srv ON srv.SiteID = s.SiteID
    LEFT JOIN Clients cli ON cli.SiteID = s.SiteID
    LEFT JOIN Radio rad ON rad.SiteID = s.SiteID
    LEFT JOIN AudioDevice aud ON aud.ClientID = cli.ClientID
    LEFT JOIN PhoneIntegration ph ON ph.SiteID = s.SiteID
    GROUP BY p.ProjectID
) t;

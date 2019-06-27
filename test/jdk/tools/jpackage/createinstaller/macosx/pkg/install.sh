echo Note: This script will install packages silently. In order to verify UI, each .pkg needs to launched manually via Finder.
sudo /usr/sbin/installer -pkg JPMacPkgTest-1.0.pkg -target /
sudo /usr/sbin/installer -pkg JPMacPkgLicenseTest-1.0.pkg -target /
sudo /usr/sbin/installer -pkg JPMacPkgAssociationsTest-1.0.pkg -target /
sudo /usr/sbin/installer -pkg JPMacOptionsTest-1.0.pkg -target /
sudo /usr/sbin/installer -pkg JPMacPkgInstallDirTest-1.0.pkg -target /

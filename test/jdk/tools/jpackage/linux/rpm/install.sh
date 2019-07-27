ARCH=$(rpmbuild -E='%{_target_cpu}')
sudo rpm --install fileassociationstest-1.0-1.${ARCH}.rpm
sudo rpm --install installdirtest-1.0-1.${ARCH}.rpm
sudo rpm --install licensetest-1.0-1.${ARCH}.rpm
sudo rpm --install licensetypetest-1.0-1.${ARCH}.rpm
sudo rpm --install packagedepstestdep-1.0-1.${ARCH}.rpm
sudo rpm --install packagedepstest-1.0-1.${ARCH}.rpm
sudo rpm --install test-1.0-1.${ARCH}.rpm
sudo rpm --install jpackage-test-bundle-name-1.0-1.${ARCH}.rpm

Summary: APPLICATION_SUMMARY
Name: APPLICATION_PACKAGE
Version: APPLICATION_VERSION
Release: APPLICATION_RELEASE
License: APPLICATION_LICENSE_TYPE
Vendor: APPLICATION_VENDOR
Prefix: INSTALLATION_DIRECTORY
Provides: APPLICATION_PACKAGE
%if "xAPPLICATION_GROUP" != x 
Group: APPLICATION_GROUP
%endif

Autoprov: 0
Autoreq: 0
PACKAGE_DEPENDENCIES

#avoid ARCH subfolder
%define _rpmfilename %%{NAME}-%%{VERSION}-%%{RELEASE}.%%{ARCH}.rpm

#comment line below to enable effective jar compression
#it could easily get your package size from 40 to 15Mb but 
#build time will substantially increase and it may require unpack200/system java to install
%define __jar_repack %{nil}

%description
APPLICATION_DESCRIPTION

%prep

%build

%install
rm -rf %{buildroot}
mkdir -p %{buildroot}INSTALLATION_DIRECTORY
cp -r %{_sourcedir}/APPLICATION_FS_NAME %{buildroot}INSTALLATION_DIRECTORY
%if "xAPPLICATION_LICENSE_FILE" != x
  %define license_install_file %{_defaultlicensedir}/%{name}-%{version}/%{basename:APPLICATION_LICENSE_FILE}
  install -d -m 755 %{buildroot}%{dirname:%{license_install_file}}
  install -m 644 APPLICATION_LICENSE_FILE %{buildroot}%{license_install_file}
%endif
 
%files
%{?license_install_file:%license %{license_install_file}}
# If installation directory for the application is /a/b/c, we want only root 
# component of the path (/a) in the spec file to make sure all subdirectories
# are owned by the package.
%(echo INSTALLATION_DIRECTORY/APPLICATION_FS_NAME | sed -e "s|\(^/[^/]\{1,\}\).*$|\1|")

%post
if [ "RUNTIME_INSTALLER" != "true" ]; then
ADD_LAUNCHERS_INSTALL
    xdg-desktop-menu install --novendor INSTALLATION_DIRECTORY/APPLICATION_FS_NAME/APPLICATION_LAUNCHER_FILENAME.desktop
FILE_ASSOCIATION_INSTALL
fi

%preun
if [ "RUNTIME_INSTALLER" != "true" ]; then
ADD_LAUNCHERS_REMOVE
    xdg-desktop-menu uninstall --novendor INSTALLATION_DIRECTORY/APPLICATION_FS_NAME/APPLICATION_LAUNCHER_FILENAME.desktop
FILE_ASSOCIATION_REMOVE
fi
if [ "SERVICE_HINT" = "true" ]; then
    if [ -x "/etc/init.d/APPLICATION_PACKAGE" ]; then
        if [ "STOP_ON_UNINSTALL" = "true" ]; then
            /etc/init.d/APPLICATION_PACKAGE stop
        fi
        /sbin/chkconfig --del APPLICATION_PACKAGE
        rm -f /etc/init.d/APPLICATION_PACKAGE
    fi
fi

%clean

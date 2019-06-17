#include <windows.h>

#include "WinSysInfo.h"
#include "FileUtils.h"
#include "Executor.h"
#include "Resources.h"
#include "WinErrorHandling.h"


int wmain(int argc, wchar_t *argv[])
{
    JP_TRY;

    // Create temporary directory where to extract msi file.
    const auto tempMsiDir = FileUtils::createTempDirectory();

    // Schedule temporary directory for deletion.
    FileUtils::Deleter cleaner;
    cleaner.appendRecursiveDirectory(tempMsiDir);

    const auto msiPath = FileUtils::mkpath() << tempMsiDir << L"main.msi";

    // Extract msi file.
    Resource(L"msi", RT_RCDATA).saveToFile(msiPath);

    // Setup executor to run msiexec
    Executor msiExecutor(SysInfo::getWIPath());
    msiExecutor.arg(L"/i").arg(msiPath);
    for (int i = 1; i < argc; ++i) {
        msiExecutor.arg(argv[i]);
    }

    // Install msi file.
    return msiExecutor.execAndWaitForExit();

    JP_CATCH_ALL;
}

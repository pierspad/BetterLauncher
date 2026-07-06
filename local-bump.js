const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

try {
    // 1. Get the last commit message
    const commitMsg = execSync('git log -1 --pretty=%B').toString().trim();
    
    // 2. Regex to parse semantic commit message: type(scope): description
    const semanticRegex = /^(feat|fix|refactor|perf|chore|build|ci|docs|style|test)(\([a-z0-9_\-\.]+\))?:\s*(.*)/i;
    const match = commitMsg.match(semanticRegex);
    
    if (!match) {
        console.log("Not a semantic commit message. Skipping version bump.");
        process.exit(0);
    }
    
    const type = match[1].toLowerCase();
    
    // 3. Read gradle.properties
    const gradlePropertiesPath = path.join(__dirname, 'gradle.properties');
    if (!fs.existsSync(gradlePropertiesPath)) {
        console.error("gradle.properties not found!");
        process.exit(1);
    }
    
    let content = fs.readFileSync(gradlePropertiesPath, 'utf8');
    
    // 4. Parse current version name and code
    const versionNameMatch = content.match(/versionName\s*=\s*(\d+\.\d+\.\d+)/);
    const versionCodeMatch = content.match(/versionCode\s*=\s*(\d+)/);
    
    if (!versionNameMatch || !versionCodeMatch) {
        console.error("Could not find versionName or versionCode in gradle.properties");
        process.exit(1);
    }
    
    const currentVersionName = versionNameMatch[1];
    const currentVersionCode = parseInt(versionCodeMatch[1], 10);
    
    // 5. Determine next version name
    const parts = currentVersionName.split('.').map(Number);
    let nextVersionName = currentVersionName;
    
    if (type === 'feat') {
        // Increment minor version, reset patch to 0
        parts[1] += 1;
        parts[2] = 0;
        nextVersionName = parts.join('.');
    } else if (type === 'fix' || type === 'perf' || type === 'refactor') {
        // Increment patch version
        parts[2] += 1;
        nextVersionName = parts.join('.');
    }
    
    const nextVersionCode = currentVersionCode + 1;
    
    // 6. Update file content
    content = content.replace(/versionName\s*=\s*.*/g, `versionName=${nextVersionName}`);
    content = content.replace(/versionCode\s*=\s*\d+/g, `versionCode=${nextVersionCode}`);
    
    fs.writeFileSync(gradlePropertiesPath, content, 'utf8');
    console.log(`[Local Bump] Bumped to ${nextVersionName} (${nextVersionCode}) due to "${type}" commit.`);
    
    // 7. Amend the commit
    execSync('git add gradle.properties');
    execSync('git commit --amend --no-edit --no-verify');
    console.log("[Local Bump] Commit amended successfully.");
    
} catch (e) {
    console.error("Error running local-bump.js:", e.message);
}

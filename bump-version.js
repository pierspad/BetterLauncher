const fs = require('fs');
const path = require('path');

// The next version is passed by semantic-release as the first argument
const nextVersion = process.argv[2];
if (!nextVersion) {
    console.error("No version provided!");
    process.exit(1);
}

const gradlePropertiesPath = path.join(__dirname, 'gradle.properties');
let content = fs.readFileSync(gradlePropertiesPath, 'utf8');

// 1. Update versionName
if (content.match(/versionName\s*=\s*/)) {
    content = content.replace(/versionName\s*=\s*.*/g, `versionName=${nextVersion}`);
    console.log(`Updated versionName to ${nextVersion}`);
} else {
    content += `\nversionName=${nextVersion}`;
    console.log(`Added versionName=${nextVersion}`);
}

// 2. Update versionCode
const versionCodeMatch = content.match(/versionCode\s*=\s*(\d+)/);
if (versionCodeMatch) {
    const currentCode = parseInt(versionCodeMatch[1], 10);
    const nextCode = currentCode + 1;
    content = content.replace(/versionCode\s*=\s*\d+/g, `versionCode=${nextCode}`);
    console.log(`Bumping versionCode from ${currentCode} to ${nextCode}`);
} else {
    // If versionCode doesn't exist, append it (starting with 109 based on previous 108)
    content += `\nversionCode=109`;
    console.log(`Adding versionCode=109`);
}

fs.writeFileSync(gradlePropertiesPath, content, 'utf8');
console.log(`Successfully updated gradle.properties!`);

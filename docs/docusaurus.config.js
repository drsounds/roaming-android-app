// @ts-check
import {themes as prismThemes} from 'prism-react-renderer';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Roaming',
  tagline: 'Turn human motion into input for any Android app or game',
  favicon: 'img/favicon.svg',

  url: 'https://drsounds.github.io',
  baseUrl: '/roaming-android-app/',
  organizationName: 'drsounds',
  projectName: 'roaming-android-app',
  trailingSlash: false,

  onBrokenLinks: 'throw',

  i18n: {defaultLocale: 'en', locales: ['en']},

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          routeBasePath: '/',
          sidebarPath: './sidebars.js',
          editUrl: 'https://github.com/drsounds/roaming-android-app/edit/main/docs/',
        },
        blog: false,
        theme: {customCss: './src/css/custom.css'},
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      navbar: {
        title: 'Roaming',
        logo: {alt: 'Roaming', src: 'img/favicon.svg'},
        items: [
          {type: 'docSidebar', sidebarId: 'docs', position: 'left', label: 'Docs'},
          {href: 'https://github.com/drsounds/roaming-android-app', label: 'GitHub', position: 'right'},
        ],
      },
      footer: {
        style: 'dark',
        copyright: `Roaming is open source under the Apache License 2.0.`,
      },
      prism: {
        theme: prismThemes.github,
        darkTheme: prismThemes.dracula,
        additionalLanguages: ['kotlin', 'java', 'csharp', 'gdscript', 'bash', 'json'],
      },
    }),
};

export default config;

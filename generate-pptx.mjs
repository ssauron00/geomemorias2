#!/usr/bin/env node
/**
 * generate-pptx.mjs — Genera presentation.pptx a partir del contenido de speech.txt
 * Usa únicamente módulos nativos de Node.js (fs, path, zlib) para crear un archivo
 * PPTX válido (que es un ZIP con XML interno según el estándar Open XML).
 */

import { writeFileSync, readFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { deflateSync } from 'zlib';

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUTPUT = join(__dirname, 'presentation.pptx');

// ─── Slide content ────────────────────────────────────────────────────────────
const slides = [
  {
    title: 'Geomemorias2',
    subtitle: 'Recordatorios Geolocalizados para Android',
    bullets: ['📍 App Android de recordatorios geolocalizados', '⏱️ Presentación: 10-15 minutos'],
    color: '1B5E20'
  },
  {
    title: 'El Problema',
    subtitle: 'Todos hemos olvidado algo importante al pasar por un lugar clave',
    bullets: [
      '🛒 ¿Olvidas comprar algo en el supermercado?',
      '💊 ¿Pasas por la farmacia y olvidas las medicinas?',
      '📄 ¿Llegas a la oficina y recuerdas el documento?',
      '⛽ ¿Te olvidas de parar a cargar gasolina?',
      '',
      'El problema: los recordatorios se basan en TIEMPO, no en UBICACIÓN.'
    ],
    color: 'B71C1C'
  },
  {
    title: 'La Solución',
    subtitle: 'Geomemorias2 — Recordatorios anclados a ubicaciones físicas',
    bullets: [
      '📝 Guardas un recordatorio con texto, coordenadas GPS y radio de activación',
      '📍 La app te avisa automáticamente cuando estás cerca',
      '🧠 No necesitas recordar — la ubicación activa el recordatorio por ti'
    ],
    color: '1B5E20'
  },
  {
    title: '¿Cómo Funciona?',
    subtitle: '6 pasos simples',
    bullets: [
      '1. Abres la app y ves un mapa interactivo',
      '2. Tocas en una ubicación para fijar un punto',
      '3. Escribes el recordatorio y defines el radio',
      '4. La app guarda y registra la geocerca',
      '5. Te olvidas de la app — ella hace el trabajo',
      '6. Al pasar cerca, recibes una notificación automática'
    ],
    color: '0D47A1'
  },
  {
    title: 'Casos de Uso Reales',
    subtitle: 'La app se adapta a múltiples escenarios',
    bullets: [
      '🛒 María — Compras: Crea recordatorios al inicio de la semana. La app le avisa al pasar por cada tienda.',
      '🚗 Carlos — Trabajo: Activa modo conducción y la app le dice por voz qué entregar en cada oficina.',
      '🏥 Ana — Salud: La app le recuerda al acercarse a la clínica y después en la farmacia.',
      '🛣️ Roberto — Viaje: Crea recordatorios para gasolina, descanso y comida. TTS mientras conduce.'
    ],
    color: '4A148C'
  },
  {
    title: '🚗 Modo Conducción',
    subtitle: 'Overlay transparente con TTS y comandos de voz',
    bullets: [
      '👆 Botones grandes de 64dp para tocar sin mirar',
      '🎤 Comando de voz para crear recordatorios hablando',
      '🔔 Notificación persistente con acción "Detener"',
      '🔋 Verificación de batería para alertas confiables',
      '🗣️ TTS lee recordatorios cercanos mientras conduces'
    ],
    color: '1B5E20'
  },
  {
    title: '🚙 Android Auto',
    subtitle: 'Integración con la pantalla del auto',
    bullets: [
      '📋 Lista de recordatorios con distancia en tiempo real',
      '🔄 Auto-refresh cada 10 segundos',
      '👆 Navegación entre lista y detalle',
      '📐 Diseño cumpliendo directrices de Android Auto',
      '🚗 Car App Library 1.4.0'
    ],
    color: '283593'
  },
  {
    title: 'Stack Tecnológico',
    subtitle: 'Todo funciona sin conexión a internet — Privacidad total',
    bullets: [
      '🔷 Kotlin — Lenguaje nativo Android',
      '💾 Room Database — Persistencia local (sin nube)',
      '🗺️ OSMDroid — Mapa open-source sin API key',
      '📍 Play Services Geofencing — Notificaciones automáticas en background',
      '⚙️ Foreground Service — Tracking en segundo plano',
      '🚗 Car App Library — Android Auto',
      '🔊 TextToSpeech — Anuncios por voz',
      '🎤 SpeechRecognizer — Comandos de voz'
    ],
    color: '006064'
  },
  {
    title: 'Arquitectura del Sistema',
    subtitle: '4 capas con comunicación asíncrona via SharedFlow',
    bullets: [
      '📱 UI — MainActivity · Driving Mode Overlay · Android Auto Screens',
      '⚙️ Servicios — DrivingModeService · DrivingTtsManager · GeofenceBroadcastReceiver',
      '💾 Datos — Room Database · ReminderDao · AppDatabaseProvider',
      '🌐 Externa — Play Services · OSMDroid · TextToSpeech · Car App Library',
      '📡 Comunicación: SharedFlow entre servicios y UI'
    ],
    color: '37474F'
  },
  {
    title: '¿Qué Nos Diferencia?',
    subtitle: '5 ventajas clave frente a otras apps',
    bullets: [
      '1️⃣ Geofencing REAL en background — Play Services despierta la app aunque esté cerrada',
      '2️⃣ Modo conducción seguro — Overlay transparente, botones grandes, TTS por voz',
      '3️⃣ Android Auto — Integración completa con la pantalla del auto',
      '4️⃣ Privacidad total — Sin cuenta, sin servidor, sin datos compartidos. Todo local.',
      '5️⃣ Open-source — OSMDroid, sin API key. Costo cero para el usuario.'
    ],
    color: '1B5E20'
  },
  {
    title: 'Estado Actual — v1.2.0',
    subtitle: 'Versión funcional con pendientes menores',
    bullets: [
      '✅ Core: Room + Geofencing + OSMDroid + UI',
      '✅ Android Auto: Lista + Detalle',
      '✅ Modo Conducción: Foreground Service + TTS',
      '✅ Comandos de Voz',
      '✅ Documentación completa con diagramas Mermaid',
      '',
      '⏳ Verificar geofencing en dispositivo real',
      '⏳ ProGuard release + firma APK',
      '⏳ Probar notificaciones en Android Auto'
    ],
    color: 'E65100'
  },
  {
    title: '¡Gracias!',
    subtitle: '',
    bullets: [
      '"No pierdas tiempo revisando tu lista de pendientes.',
      'La app te avisa cuando estás cerca de lo que necesitas hacer."',
      '',
      '📱 Geomemorias2 — Recordatorios Geolocalizados',
      '🔗 github.com/ssauron00/geomemorias2',
      '',
      '¿Preguntas?'
    ],
    color: '1B5E20'
  }
];

// ─── Open XML helpers ─────────────────────────────────────────────────────────

/** Create a minimal PPTX ZIP structure manually using deflate */
function createPptx(slidesData) {
  const files = [];

  // Helper to add a file entry
  const addFile = (name, content) => {
    files.push({
      name,
      data: typeof content === 'string' ? Buffer.from(content, 'utf8') : content
    });
  };

  // [Content_Types].xml
  addFile('[Content_Types].xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
  ${slidesData.map((_, i) => `<Override PartName="/ppt/slides/slide${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>`).join('\n  ')}
  <Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>
  <Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>
  <Override PartName="/ppt/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>
</Types>`);

  // _rels/.rels
  addFile('_rels/.rels', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>`);

  // ppt/theme/theme1.xml
  addFile('ppt/theme/theme1.xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="Geomemorias">
  <a:themeElements>
    <a:clrScheme name="Dark">
      <a:dk1><a:srgbClr val="1A1A2E"/></a:dk1>
      <a:lt1><a:srgbClr val="FFFFFF"/></a:lt1>
      <a:dk2><a:srgbClr val="4FC3F7"/></a:dk2>
      <a:lt2><a:srgbClr val="E0E0E0"/></a:lt2>
      <a:accent1><a:srgbClr val="4FC3F7"/></a:accent1>
      <a:accent2><a:srgbClr val="81C784"/></a:accent2>
      <a:accent3><a:srgbClr val="FFB74D"/></a:accent3>
      <a:hlink><a:srgbClr val="4FC3F7"/></a:hlink>
      <a:folHlink><a:srgbClr val="81C784"/></a:folHlink>
    </a:clrScheme>
    <a:fontScheme name="Geomemorias">
      <a:majorFont><a:latin typeface="Segoe UI"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont>
      <a:minorFont><a:latin typeface="Segoe UI"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont>
    </a:fontScheme>
    <a:fmtScheme name="Office">
      <a:fillStyleLst>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
      </a:fillStyleLst>
      <a:lnStyleLst>
        <a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
        <a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
        <a:ln w="9525"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:ln>
      </a:lnStyleLst>
      <a:effectStyleLst>
        <a:effectStyle><a:effectLst/></a:effectStyle>
        <a:effectStyle><a:effectLst/></a:effectStyle>
        <a:effectStyle><a:effectLst/></a:effectStyle>
      </a:effectStyleLst>
      <a:bgFillStyleLst>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
        <a:solidFill><a:schemeClr val="phClr"/></a:solidFill>
      </a:bgFillStyleLst>
    </a:fmtScheme>
  </a:themeElements>
</a:theme>`);

  // ppt/slideLayouts/slideLayout1.xml
  addFile('ppt/slideLayouts/slideLayout1.xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank" preserve="1">
  <p:cSld>
    <p:bg>
      <p:bgRef idx="1001"><a:schemeClr val="dk1"/></p:bgRef>
    </p:bg>
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
    </p:spTree>
  </p:cSld>
  <p:clrMapOvr>
    <a:masterClrMapping/>
  </p:clrMapOvr>
</p:sldLayout>`);

  // ppt/slideMasters/slideMaster1.xml
  addFile('ppt/slideMasters/slideMaster1.xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:bg>
      <p:bgRef idx="1001"><a:schemeClr val="dk1"/></p:bgRef>
    </p:bg>
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
    </p:spTree>
  </p:cSld>
  <p:clrMap bg1="dk1" tx1="lt1" bg2="dk2" tx2="lt2" accent1="accent1" accent2="accent2" accent3="accent3" hlink="hlink" folHlink="folHlink"/>
  <p:sldLayoutIdLst>
    <p:sldLayoutId id="2147483649" r:id="rId1"/>
  </p:sldLayoutIdLst>
  <p:txStyles>
    <p:titleStyle>
      <a:lvl1pPr algn="l"><a:defRPr sz="4400" b="1"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill><a:latin typeface="Segoe UI"/></a:defRPr></a:lvl1pPr>
    </p:titleStyle>
    <p:bodyStyle>
      <a:lvl1pPr marL="457200" indent="-457200"><a:defRPr sz="2400"><a:solidFill><a:srgbClr val="E0E0E0"/></a:solidFill><a:latin typeface="Segoe UI"/></a:defRPr></a:lvl1pPr>
    </p:bodyStyle>
    <p:otherStyle>
      <a:lvl1pPr><a:defRPr sz="2000"><a:latin typeface="Segoe UI"/></a:defRPr></a:lvl1pPr>
    </p:otherStyle>
  </p:txStyles>
</p:sldMaster>`);

  // ppt/_rels/slideMasters/slideMaster1.xml.rels
  addFile('ppt/_rels/slideMasters/slideMaster1.xml.rels', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="../theme/theme1.xml"/>
</Relationships>`);

  // ppt/_rels/slideLayouts/slideLayout1.xml.rels
  addFile('ppt/_rels/slideLayouts/slideLayout1.xml.rels', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
</Relationships>`);

  // Slide files
  const slideRels = [];
  slidesData.forEach((slide, i) => {
    const num = i + 1;
    const slideFile = `ppt/slides/slide${num}.xml`;
    const bulletXml = slide.bullets
      .filter(b => b !== '')
      .map((b, bi) => {
        // Determine bullet color based on emoji prefix
        let bulletColor = 'E0E0E0';
        if (b.startsWith('✅')) bulletColor = '81C784';
        else if (b.startsWith('⏳')) bulletColor = 'FFB74D';
        else if (b.startsWith('❌')) bulletColor = 'EF5350';
        else if (b.startsWith('1️⃣') || b.startsWith('2️⃣') || b.startsWith('3️⃣') || b.startsWith('4️⃣') || b.startsWith('5️⃣')) bulletColor = '81C784';

        const cleanText = b.replace(/^[0-9️⃣]+️⃣\s*/, '').replace(/^[✅⏳❌]\s*/, '');
        return `
        <a:p>
          <a:pPr indent="0" marL="342900">
            <a:buFont typeface="Arial" panose="020B0604020202020204" pitchFamily="34" charset="0"/>
            <a:buClr><a:srgbClr val="4FC3F7"/></a:buClr>
            <a:buSzPct val="80000"/>
          </a:pPr>
          <a:r>
            <a:rPr lang="es-MX" altLang="en-US" sz="1800" dirty="0">
              <a:solidFill><a:srgbClr val="${bulletColor}"/></a:solidFill>
              <a:latin typeface="Segoe UI"/>
            </a:rPr>
            <a:t>${escapeXml(cleanText)}</a:t>
          </a:r>
        </a:p>`;
      }).join('');

    addFile(slideFile, `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:bg>
      <p:bgRef idx="1001"><a:schemeClr val="dk1"/></p:bgRef>
    </p:bg>
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>
      <!-- Background color -->
      <p:sp>
        <p:nvSpPr><p:cNvPr id="2" name="BG"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph/></p:nvPr></p:nvSpPr>
        <p:spPr>
          <a:xfrm><a:off x="0" y="0"/><a:ext cx="9144000" cy="5143500"/></a:xfrm>
          <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
          <a:solidFill><a:srgbClr val="${slide.color}"/></a:solidFill>
        </p:spPr>
        <p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:endParaRPr lang="es-MX"/></a:p></p:txBody>
      </p:sp>
      <!-- Title -->
      <p:sp>
        <p:nvSpPr><p:cNvPr id="3" name="Title"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph type="title"/></p:nvPr></p:nvSpPr>
        <p:spPr>
          <a:xfrm><a:off x="457200" y="273050"/><a:ext cx="8229600" cy="685800"/></a:xfrm>
          <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
        </p:spPr>
        <p:txBody>
          <a:bodyPr wrap="square" lIns="91440" tIns="45720" rIns="91440" bIns="45720"/>
          <a:lstStyle/>
          <a:p>
            <a:pPr algn="l"/>
            <a:r>
              <a:rPr lang="es-MX" altLang="en-US" sz="3600" b="1" dirty="0">
                <a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill>
                <a:latin typeface="Segoe UI"/>
              </a:rPr>
              <a:t>${escapeXml(slide.title)}</a:t>
            </a:r>
          </a:p>
        </p:txBody>
      </p:sp>
      <!-- Subtitle -->
      ${slide.subtitle ? `<!-- Subtitle -->
      <p:sp>
        <p:nvSpPr><p:cNvPr id="4" name="Subtitle"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph idx="1"/></p:nvPr></p:nvSpPr>
        <p:spPr>
          <a:xfrm><a:off x="457200" y="958850"/><a:ext cx="8229600" cy="411475"/></a:xfrm>
          <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
        </p:spPr>
        <p:txBody>
          <a:bodyPr wrap="square" lIns="91440" tIns="45720" rIns="91440" bIns="45720"/>
          <a:lstStyle/>
          <a:p>
            <a:pPr algn="l"/>
            <a:r>
              <a:rPr lang="es-MX" altLang="en-US" sz="2000" i="1" dirty="0">
                <a:solidFill><a:srgbClr val="B0BEC5"/></a:solidFill>
                <a:latin typeface="Segoe UI"/>
              </a:rPr>
              <a:t>${escapeXml(slide.subtitle)}</a:t>
            </a:r>
          </a:p>
        </p:txBody>
      </p:sp>` : ''}
      <!-- Bullets -->
      <p:sp>
        <p:nvSpPr><p:cNvPr id="5" name="Content"/><p:cNvSpPr><a:spLocks noGrp="1"/></p:cNvSpPr><p:nvPr><p:ph idx="5"/></p:nvPr></p:nvSpPr>
        <p:spPr>
          <a:xfrm><a:off x="457200" y="${slide.subtitle ? '1417325' : '1046350'}"/><a:ext cx="8229600" cy="${slide.subtitle ? '3499025' : '3872900'}"/></a:xfrm>
          <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
        </p:spPr>
        <p:txBody>
          <a:bodyPr wrap="square" lIns="91440" tIns="45720" rIns="91440" bIns="45720" anchor="t"/>
          <a:lstStyle/>
          ${bulletXml}
        </p:txBody>
      </p:sp>
    </p:spTree>
  </p:cSld>
  <p:clrMapOvr>
    <a:masterClrMapping/>
  </p:clrMapOvr>
</p:sld>`);

    slideRels.push({ num, title: slide.title });
  });

  // Slide relationship files
  slideRels.forEach(({ num }) => {
    addFile(`ppt/_rels/slides/slide${num}.xml.rels`, `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
</Relationships>`);
  });

  // ppt/_rels/presentation.xml.rels
  addFile('ppt/_rels/presentation.xml.rels', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme" Target="theme/theme1.xml"/>
  ${slidesData.map((_, i) => `<Relationship Id="rId${i + 10}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide${i + 1}.xml"/>`).join('\n  ')}
</Relationships>`);

  // ppt/presentation.xml
  addFile('ppt/presentation.xml', `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:sldMasterIdLst>
    <p:sldMasterId id="2147483648" r:id="rId1"/>
  </p:sldMasterIdLst>
  <p:sldIdLst>
    ${slidesData.map((_, i) => `<p:sldId id="${256 + i}" r:id="rId${i + 10}"/>`).join('\n    ')}
  </p:sldIdLst>
  <p:sldSz cx="9144000" cy="5143500" type="screen4x3"/>
  <p:notesSz cx="6858000" cy="9144000"/>
</p:presentation>`);

  // ─── Build ZIP ────────────────────────────────────────────────────────────
  return buildZip(files);
}

/** Escape XML special characters */
function escapeXml(str) {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

/**
 * Build a ZIP file from an array of { name, data } entries.
 * Uses deflate compression for each file.
 */
function buildZip(files) {
  const localHeaders = [];
  const centralHeaders = [];
  let offset = 0;

  const encoder = new TextEncoder();

  files.forEach(({ name, data }) => {
    const nameBytes = Buffer.from(encoder.encode(name));
    const compressed = deflateSync(data);

    // Local file header (30 + name length)
    const localHeader = Buffer.alloc(30 + nameBytes.length);
    localHeader.writeUInt32LE(0x04034b50, 0); // signature
    localHeader.writeUInt16LE(20, 4); // version needed
    localHeader.writeUInt16LE(0, 6); // flags
    localHeader.writeUInt16LE(8, 8); // compression method (deflate)
    localHeader.writeUInt16LE(0, 10); // mod time
    localHeader.writeUInt16LE(0, 12); // mod date
    localHeader.writeUInt32LE(crc32(data), 14); // crc32
    localHeader.writeUInt32LE(compressed.length, 18); // compressed size
    localHeader.writeUInt32LE(data.length, 22); // uncompressed size
    localHeader.writeUInt16LE(nameBytes.length, 26); // name length
    localHeader.writeUInt16LE(0, 28); // extra field length
    nameBytes.copy(localHeader, 30);

    // Central directory entry (46 + name length)
    const centralEntry = Buffer.alloc(46 + nameBytes.length);
    centralEntry.writeUInt32LE(0x02014b50, 0); // signature
    centralEntry.writeUInt16LE(20, 4); // version made by
    centralEntry.writeUInt16LE(20, 6); // version needed
    centralEntry.writeUInt16LE(0, 8); // flags
    centralEntry.writeUInt16LE(8, 10); // compression method
    centralEntry.writeUInt16LE(0, 12); // mod time
    centralEntry.writeUInt16LE(0, 14); // mod date
    centralEntry.writeUInt32LE(crc32(data), 16); // crc32
    centralEntry.writeUInt32LE(compressed.length, 20); // compressed size
    centralEntry.writeUInt32LE(data.length, 24); // uncompressed size
    centralEntry.writeUInt16LE(nameBytes.length, 28); // name length
    centralEntry.writeUInt16LE(0, 30); // extra field length
    centralEntry.writeUInt16LE(0, 32); // file comment length
    centralEntry.writeUInt16LE(0, 34); // disk number start
    centralEntry.writeUInt16LE(0, 36); // internal file attributes
    centralEntry.writeUInt32LE(0, 38); // external file attributes
    centralEntry.writeUInt32LE(offset, 42); // relative offset of local header
    nameBytes.copy(centralEntry, 46);

    localHeaders.push(Buffer.concat([localHeader, compressed]));
    centralHeaders.push(centralEntry);
    offset += localHeader.length + compressed.length;
  });

  const centralDirOffset = offset;
  const centralDirBuffer = Buffer.concat(centralHeaders);
  const centralDirSize = centralDirBuffer.length;

  // End of central directory
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(0, 4); // disk number
  eocd.writeUInt16LE(0, 6); // disk with central dir
  eocd.writeUInt16LE(files.length, 8); // entries on this disk
  eocd.writeUInt16LE(files.length, 10); // total entries
  eocd.writeUInt32LE(centralDirSize, 12);
  eocd.writeUInt32LE(centralDirOffset, 16);
  eocd.writeUInt16LE(0, 20); // comment length

  return Buffer.concat([...localHeaders, centralDirBuffer, eocd]);
}

/** CRC32 implementation */
function crc32(buf) {
  let crc = 0xFFFFFFFF;
  for (let i = 0; i < buf.length; i++) {
    crc ^= buf[i];
    for (let j = 0; j < 8; j++) {
      crc = (crc >>> 1) ^ (crc & 1 ? 0xEDB88320 : 0);
    }
  }
  return (crc ^ 0xFFFFFFFF) >>> 0;
}

// ─── Main ─────────────────────────────────────────────────────────────────────
try {
  console.log('Generando presentation.pptx...');
  const pptx = createPptx(slides);
  writeFileSync(OUTPUT, pptx);
  console.log(`✅ presentation.pptx creado (${pptx.length} bytes, ${slides.length} diapositivas)`);
} catch (err) {
  console.error('❌ Error generando PPTX:', err.message);
  process.exit(1);
}

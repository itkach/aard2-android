#!/usr/bin/env python3

# PROJECTS = ['wikipedia', 'wiktionary', 'wikiquote']

PROJECTS = ['wikipedia', 'wiktionary']

"""
From https://meta.wikimedia.org/wiki/List_of_Wikipedias

Run in js console following:

var langs = new Set()
$('td:nth-child(4) a.extiw').each((x, y) => langs.add(`'${y.text}'`))
langs = Array.from(langs).sort()
console.log(langs.join())

"""

# LANGUAGES = [
# 'aa','ab','ace','af','ak','als','am','an','ang','ar','arc','arz','as','ast','av','ay','az','azb','ba','bar','bat-smg','bcl','be','be-tarask','bg','bh','bi','bjn','bm','bn','bo','bpy','br','bs','bug','bxr','ca','cbk-zam','cdo','ce','ceb','ch','cho','chr','chy','ckb','co','cr','crh','cs','csb','cu','cv','cy','da','de','diq','dsb','dv','dz','ee','el','eml','en','eo','es','et','eu','ext','fa','ff','fi','fiu-vro','fj','fo','fr','frp','frr','fur','fy','ga','gag','gan','gd','gl','glk','gn','gom','got','gu','gv','ha','hak','haw','he','hi','hif','ho','hr','hsb','ht','hu','hy','hz','ia','id','ie','ig','ii','ik','ilo','io','is','it','iu','ja','jbo','jv','ka','kaa','kab','kbd','kg','ki','kj','kk','kl','km','kn','ko','koi','kr','krc','ks','ksh','ku','kv','kw','ky','la','lad','lb','lbe','lez','lg','li','lij','lmo','ln','lo','lrc','lt','ltg','lv','mai','map-bms','mdf','mg','mh','mhr','mi','min','mk','ml','mn','mo','mr','mrj','ms','mt','mus','mwl','my','myv','mzn','na','nah','nap','nds','nds-nl','ne','new','ng','nl','nn','no','nov','nrm','nso','nv','ny','oc','om','or','os','pa','pag','pam','pap','pcd','pdc','pfl','pi','pih','pl','pms','pnb','pnt','ps','pt','qu','rm','rmy','rn','ro','roa-rup','roa-tara','ru','rue','rw','sa','sah','sc','scn','sco','sd','se','sg','sh','si','simple','sk','sl','sm','sn','so','sq','sr','srn','ss','st','stq','su','sv','sw','szl','ta','te','tet','tg','th','ti','tk','tl','tn','to','tpi','tr','ts','tt','tum','tw','ty','tyv','udm','ug','uk','ur','uz','ve','vec','vep','vi','vls','vo','wa','war','wo','wuu','xal','xh','xmf','yi','yo','za','zea','zh','zh-classical','zh-min-nan','zh-yue','zu'
# ]

#Android 6.0.1 decided it can no longer handle large manifest and craps out, so enable links for just a few languages

LANGUAGES = [
    'ar',
    'de',
    'en',
    'es',
    'fa',
    'fr',
    'it',
    'ja',
    'nl',
    'pl',
    'pt',
    'ru',
    'uk',
    'zh'
]


def main():

    with open('./AndroidManifest.template.xml') as f:
        manifest_template = f.read()

    with open('./wikipedia-activity.template.xml') as f:
        wikipedia_template = f.read()

    activities = []

    for lang in LANGUAGES:
        for project in PROJECTS:
            activity = wikipedia_template.format(lang=lang, project=project)
            activities.append(activity)

    manifest = manifest_template.format(wikipedia_activities='\n'.join(activities))

    with open('AndroidManifest.xml', 'w') as f:
        f.write(manifest)

main()

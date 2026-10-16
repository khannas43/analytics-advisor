#!/usr/bin/env python3
"""Generate gender-tagged first/middle pools and single-token surname pool (letters only)."""
from __future__ import annotations

import pathlib
import re
import sys

OUT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "docker/demo/name_pools")

MALE_FIRST = [
    "Aarav", "Aakash", "Ajay", "Akash", "Alok", "Amit", "Anil", "Ankit", "Arjun", "Arvind",
    "Ashok", "Bablu", "Bhagwan", "Bheru", "Bhim", "Chetan", "Daksh", "Deepak", "Dhanraj", "Dharmendra",
    "Dinesh", "Ganesh", "Girdhari", "Gopal", "Govind", "Gulab", "Hari", "Harish", "Hukam", "Jagdish",
    "Kailash", "Karan", "Kirtan", "Krishna", "Kundan", "Lalit", "Laxman", "Mahesh", "Manoj", "Mohan",
    "Mukesh", "Naresh", "Om", "Pankaj", "Prakash", "Prem", "Punit", "Rahul", "Rajesh", "Rakesh",
    "Ram", "Ramesh", "Ravi", "Rohit", "Sagar", "Sanjay", "Santosh", "Satya", "Shankar", "Shyam",
    "Sohan", "Sonu", "Sunil", "Suresh", "Tarun", "Umesh", "Vijay", "Vikram", "Vinod", "Vishnu",
    "Yogesh", "Bhanwar", "Chhotu", "Ghasi", "Gordhan", "Hanuman", "Heera", "Jagat", "Jitu", "Kalu",
    "Kesar", "Khuma", "Mangilal", "Motilal", "Nathu", "Pappu", "Ramu", "Tej", "Banna", "Bhanu",
    "Dhannu", "Mohammad", "Lakhan", "Shyamlal", "Ghasiram", "Jagannath", "Narayan", "Sitaram", "Bhagirath",
    "Krishan", "Mohammed", "Abdul", "Imran", "Salman", "Nadeem", "Hariram", "Radhey", "Kanhaiya",
    "Gopaldas", "Ramkishan", "Shivram", "Balram", "Gangaram", "Devendra", "Rajendra", "Surendra", "Mahendra",
    "Brijesh", "Mukund", "Ghanshyam", "Brijmohan", "Ramchandra", "Shivkumar", "Raghunath", "Damodar",
    "Amar", "Anup", "Arun", "Atul", "Badri", "Banwari", "Bhola", "Brij", "Chandra", "Charan",
    "Dilip", "Faruk", "Gaurav", "Gopal", "Hardayal", "Hiralal", "Inder", "Jagannath", "Jaswant", "Jayant",
    "Jitendra", "Kamal", "Kamal", "Kamal", "Kapil", "Kishore", "Lokesh", "Madan", "Madhusudan", "Maniram",
    "Murali", "Nand", "Narendra", "Naveen", "Niranjan", "Pawan", "Pratap", "Raghav", "Rajan", "Rameshwar",
    "Ranjit", "Ratan", "Ravindra", "Rishabh", "Rudra", "Sachin", "Salim", "Sameer", "Sanjiv", "Sarvesh",
    "Satyendra", "Shivam", "Shivendra", "Shobharam", "Subhash", "Sudhir", "Sukhdev", "Sultan", "Trilok", "Uday",
    "Varun", "Vikas", "Vinay", "Virat", "Yash", "Yusuf", "Zakir", "Abhay", "Aditya", "Akshay",
    "Anand", "Anshul", "Arpit", "Ashish", "Avinash", "Bhagat", "Bhola", "Chandrapal", "Dharm", "Dilbag",
    "Fateh", "Gajendra", "Gopal", "Goverdhan", "Hansraj", "Hemant", "Himmat", "Ishwar", "Jagmal", "Jasbir",
    "Jeevan", "Kailash", "Kishan", "Krishan", "Laxman", "Madan", "Mahavir", "Manphool", "Mewa", "Nahar",
    "Nand", "Naresh", "Nathuram", "Onkar", "Pappu", "Parshuram", "Phool", "Puran", "Purushottam", "Raghu",
    "Raja", "Rajpal", "Ramavatar", "Ramdev", "Ramgopal", "Ramniwas", "Ramsingh", "Randhir", "Ranveer", "Ravinder",
    "Rohtash", "Roopsingh", "Sahib", "Sajjan", "Samunder", "Sardar", "Sher", "Shivdayal", "Shobha", "Sohan",
    "Sukhram", "Sultan", "Surajmal", "Tejpal", "Thakur", "Tilak", "Uday", "Ummed", "Ved", "Vijaypal",
    "Virender", "Zorawar", "Balwant", "Bhagirath", "Chhote", "Daulat", "Fakir", "Girdhari", "Hukum", "Jagat",
]

FEMALE_FIRST = [
    "Anita", "Ankita", "Babita", "Bhavna", "Chanda", "Deepa", "Devki", "Divya", "Ganga", "Gauri",
    "Gayatri", "Geeta", "Hemlata", "Indira", "Jyoti", "Kamla", "Kavita", "Kiran", "Lakshmi", "Lata",
    "Leela", "Mamta", "Manisha", "Meena", "Neha", "Nirmala", "Parvati", "Pooja", "Priya", "Radha",
    "Rani", "Rekha", "Sapna", "Saroj", "Seema", "Shanti", "Sita", "Sudha", "Suman", "Sunita",
    "Swati", "Usha", "Vimla", "Yashoda", "Bhawani", "Bina", "Champa", "Devi", "Mira", "Phool",
    "Poonam", "Sharda", "Shobha", "Tulsi", "Urmila", "Bai", "Fatima", "Ayesha", "Parveen", "Shabana",
    "Premwati", "Shyamoli", "Gangawati", "Savitri", "Kaushalya", "Subhadra", "Ahilya", "Anusuya", "Madhu",
    "Madhubala", "Pushpa", "Kamlesh", "Sumanlata", "Manjulata", "Rajkumari", "Phoolwati", "Chandrawati",
    "Gyanwati", "Lilawati", "Dropadi", "Rukmini", "Radhika", "Mohini", "Kamini", "Lalita", "Malti",
    "Sushila", "Ramwati", "Janki", "Vidyawati", "Shakuntala", "Damayanti", "Sunanda", "Neelam", "Rekha",
    "Sandhya", "Asha", "Nisha", "Priti", "Ritu", "Simran", "Tanvi", "Aditi", "Isha", "Komal",
    "Aarti", "Alpana", "Amrita", "Anjali", "Anju", "Anupama", "Archana", "Aruna", "Asha", "Bharti",
    "Bhavana", "Chandni", "Charu", "Damini", "Deepika", "Dimple", "Garima", "Gita", "Heena", "Hema",
    "Hina", "Jaya", "Jayanti", "Kalpana", "Kamini", "Kanchan", "Kanta", "Karuna", "Kusum", "Lalita",
    "Madhu", "Madhuri", "Mala", "Mamta", "Manju", "Meenakshi", "Menka", "Mira", "Mohini", "Mona",
    "Mukta", "Nandini", "Neeta", "Neetu", "Nikita", "Nisha", "Padma", "Pallavi", "Payal", "Pinky",
    "Pooja", "Pratibha", "Preeti", "Priyanka", "Rachna", "Ragini", "Rajni", "Rashmi", "Renu", "Rina",
    "Ritu", "Roshni", "Ruby", "Rukhsar", "Salma", "Sangeeta", "Sanju", "Sarla", "Shailja", "Shashi",
    "Sheela", "Shila", "Shivani", "Shobha", "Shruti", "Smita", "Sneha", "Sonia", "Sudha", "Sujata",
    "Sulochana", "Suman", "Sunita", "Supriya", "Sushma", "Tarannum", "Uma", "Urmila", "Varsha", "Veena",
    "Vibha", "Vidya", "Vimala", "Vinita", "Vandana", "Yamini", "Yasmin", "Zarina", "Zubeda", "Afreen",
    "Ambika", "Anjana", "Anuradha", "Aparna", "Arti", "Bimla", "Chameli", "Chandana", "Chhaya", "Daksha",
    "Draupadi", "Ekta", "Gayatri", "Gulab", "Gulabo", "Hansa", "Himani", "Ila", "Ira", "Jyotsna",
    "Kamala", "Kanti", "Kasturi", "Ketaki", "Kirti", "Krishna", "Lalita", "Laxmi", "Lila", "Malati",
    "Manorama", "Maya", "Mrinalini", "Nalini", "Nandita", "Navita", "Nirmala", "Nutan", "Padmini", "Pramila",
    "Prerna", "Priti", "Purnima", "Rama", "Ramkali", "Ranjana", "Rashmi", "Ratna", "Renuka", "Riddhi",
    "Roma", "Ruchi", "Rukmini", "Sadhna", "Sakshi", "Saloni", "Samita", "Sangita", "Sanjogita", "Sarika",
    "Sarojini", "Shakuntala", "Shanta", "Sharda", "Shefali", "Shivangi", "Shobhna", "Shraddha", "Shyama", "Siddhi",
    "Smita", "Snehlata", "Sona", "Sonali", "Subhadra", "Suchitra", "Sujata", "Sukanya", "Sulakshana", "Sushmita",
    "Tarawati", "Trisha", "Tulika", "Ujjwala", "Uma", "Urmi", "Urvashi", "Vaishali", "Vani", "Vasundhara",
]

MALE_MIDDLE = [
    "Kumar", "Prasad", "Narayan", "Kishan", "Chand", "Ram", "Lal", "Shyam", "Hari", "Gopal",
    "Shankar", "Manohar", "Sunder", "Babu", "Mohan", "Nath", "Das", "Ghanshyam", "Radhey", "Balbir",
    "Suraj", "Ramesh", "Suresh", "Mahesh", "Dinesh", "Rakesh", "Mukesh", "Rajesh", "Ganesh", "Brijesh",
    "Krishan", "Sitaram", "Jagdish", "Harish", "Satish", "Rameshwar", "Shivnarayan", "Ramkishan", "Girdhari",
    "Bhanwar", "Kanhaiya", "Govind", "Murari", "Banwari", "Kedarnath", "Ghasiram", "Motilal", "Shyamlal",
    "Bhagwan", "Hanuman", "Lakshman", "Bharat", "Shatrughan", "Devkinandan", "Ramprasad", "Shivprasad",
    "Ramgopal", "Shivram", "Balram", "Premchand", "Ramchandra", "Om Prakash",
    "Amarnath", "Badri", "Bankey", "Bhagwat", "Bhola", "Brijmohan", "Chhotelal", "Dayaram", "Dwarika", "Fateh",
    "Gajraj", "Goverdhan", "Hansraj", "Hariom", "Hukam", "Ishwar", "Jagat", "Jawahar", "Kedarnath", "Khem",
    "Madan", "Mahavir", "Maniram", "Mewaram", "Nand", "Nandkishore", "Nathuram", "Onkar", "Phool",
    "Purushottam", "Raghunath", "Ramavtar", "Ramdev", "Ramniwas", "Randhir", "Rohtash", "Sahib", "Sajjan", "Sher",
    "Shivdayal", "Sukhdev", "Surajmal", "Tejpal", "Tilak", "Ummed", "Ved", "Vijaypal", "Virender", "Zorawar",
]

FEMALE_MIDDLE = [
    "Devi", "Kumari", "Bai", "Rani", "Wati", "Shanti", "Ganga", "Parvati", "Sita", "Radha",
    "Lakshmi", "Saraswati", "Durga", "Uma", "Gauri", "Savitri", "Annapurna", "Kamala", "Maya", "Leela",
    "Geeta", "Seeta", "Rekha", "Asha", "Usha", "Lata", "Nirmala", "Shobha", "Suman", "Kamla",
    "Indira", "Poonam", "Sunita", "Manju", "Sushila", "Vidya", "Gyanwati", "Phoolwati", "Chandrawati",
    "Lilawati", "Premwati", "Ramwati", "Shyamwati", "Gangawati", "Savitri", "Kaushalya", "Subhadra",
    "Ahilya", "Anusuya", "Madhu", "Pushpa", "Sandhya", "Neelam", "Kiran", "Swati", "Jyoti", "Kavita",
    "Sapna", "Divya", "Meena", "Saroj",     "Hemlata", "Kamlesh", "Manisha", "Babita", "Bhavna", "Champa",
    "Amba", "Anjali", "Bhamini", "Chandni", "Damini", "Girija", "Himani", "Jaya", "Kalpana", "Kamala",
    "Kanti", "Kasturi", "Ketaki", "Kirti", "Malati", "Nalini", "Padmini", "Purnima", "Rama", "Rukmini",
]

# "Shyam Sunder" and "Ram Lal" are two tokens — use only single-token middles for max-3-token full names.
MALE_MIDDLE = [n for n in MALE_MIDDLE if " " not in n and len(n) >= 3]
FEMALE_MIDDLE = [n for n in FEMALE_MIDDLE if " " not in n and len(n) >= 3 and n != "Wati"]

LAST_BASE = [
    "Sharma", "Verma", "Meena", "Yadav", "Gurjar", "Rajput", "Tanwar", "Bairwa", "Meghwal",
    "Kumhar", "Saini", "Prajapati", "Malviya", "Saxena", "Tiwari", "Mishra", "Dutt", "Bhatia", "Kapoor",
    "Khanna", "Gill", "Rathore", "Solanki", "Parmar", "Deora", "Bana", "Charan", "Godara", "Lohar",
    "Nai", "Regar", "Sahariya", "Kathodi", "Kharadi", "Koli", "Garasia", "Damor", "Dhanaka",
    "Singh", "Kumar", "Choudhary", "Chaudhary", "Joshi", "Agarwal", "Goyal", "Goel", "Gupta", "Bansal",
    "Mittal", "Arora", "Sethi", "Bhandari", "Sogani", "Lodha", "Somani", "Ranka", "Khandelwal", "Ojha",
    "Vyas", "Dave", "Bhatt", "Purohit", "Acharya", "Trivedi", "Pandya", "Desai", "Patel", "Shah",
    "Jain", "Bohra", "Soni", "Srivastava", "Tripathi", "Dubey", "Shukla", "Pathak", "Awasthi", "Nagar",
    "Bhardwaj", "Chaturvedi", "Dixit", "Garg", "Khatri", "Lohia", "Mathur", "Nanda", "Parikh", "Rastogi",
    "Sengar", "Tomar", "Chauhan", "Panwar", "Katoch", "Bhati", "Hada", "Chundawat", "Shekhawat",
    "Nathawat", "Khangarot", "Ranawat", "Sisodia", "Shaktawat", "Parihar", "Champawat", "Jadon", "Kachhwaha", "Naruka",
    "Johiya", "Sihag", "Punia", "Beniwal", "Malik", "Dahiya", "Phogat", "Dalal", "Hooda", "Kadian",
    "Nain", "Nehra", "Rathi", "Sangwan", "Sheoran", "Ahlawat", "Kundu", "Nagaur", "Bishnoi", "Maurya",
    "Kashyap", "Bairagi", "Swami", "Tyagi", "Bagri", "Dudi", "Mirdha", "Siyol", "Chandel", "Mertiya",
    "Sankhla", "Siyag", "Tak", "Bhadu", "Burath", "Chhabra", "Gahlot", "Jakhar", "Kharra", "Lamba",
    "Nagpal", "Pilania", "Rana", "Sanghi", "Takhar", "Uppal", "Wadhwa", "Ameta", "Barfa", "Chhipa",
    "Bhil", "Tanwar", "Meena", "Rewar", "Dahiya", "Saini", "Jat", "Gurjar", "Yadav", "Rajput",
]

LAST_EXTRA = [
    "Chopra", "Khurana", "Suri", "Bedi", "Anand", "Bhasin", "Sood", "Bajaj", "Sabharwal", "Talwar",
    "Walia", "Seth", "Batra", "Narang", "Kohli", "Suri", "Handa", "Manchanda", "Saluja", "Grover",
    "Chawla", "Passi", "Sahni", "Tandon", "Wadhawan", "Maini", "Kalra", "Luthra", "Madan", "Puri",
    "Bagga", "Sibal", "Dhawan", "Sawhney", "Kakkar", "Mittal", "Jindal", "Gupta", "Singhal", "Aggarwal",
]

TOKEN = re.compile(r"^[A-Za-z]{3,}$")
FULL_NAME = re.compile(r"^[A-Za-z]{3,}( [A-Za-z]{3,}){0,2}$")


def _single_token(name: str) -> bool:
    return bool(TOKEN.match(name))


def _dedupe_pad(names: list[str], target: int) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for n in names:
        if n in seen or not _single_token(n):
            continue
        seen.add(n)
        out.append(n)
    if len(out) < target:
        raise RuntimeError(f"pool short: need {target}, have {len(out)} unique valid names")
    return out[:target]


def _fill_last_names(base: list[str], target: int) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for n in base:
        if n not in seen and _single_token(n):
            seen.add(n)
            out.append(n)
    roots = [
        "Ram", "Lal", "Dev", "Raj", "Nar", "Sur", "Bal", "Gop", "Gov", "Moh",
        "Shiv", "Shyam", "Hari", "Kish", "Raghu", "Bhan", "Chan", "Deep", "Dhar", "Jag",
        "Kam", "Kun", "Man", "Pra", "Rad", "Sat", "Sun", "Tar", "Uda", "Vin",
        "Vis", "Agar", "Bans", "Chan", "Chau", "Gaur", "Jaat", "Jain", "Kuma", "Meen",
        "Pari", "Rath", "Sena", "Tanw", "Yad", "Gurj", "Megh", "Sain", "Praj", "Malv",
    ]
    tails = [
        "pur", "pura", "garh", "wala", "wal", "pati", "nath", "das", "ani", "ot",
        "iya", "eria", "iya", "singh", "raj", "pal", "war", "was", "wan", "iya",
    ]
    idx = 0
    while len(out) < target:
        r = roots[idx % len(roots)]
        t = tails[(idx // len(roots)) % len(tails)]
        cand = (r + t).capitalize() if idx % 5 else r + t
        if len(cand) >= 3 and cand not in seen and _single_token(cand):
            seen.add(cand)
            out.append(cand)
        idx += 1
        if idx > target * 50:
            raise RuntimeError(f"fill_last stuck at {len(out)}/{target}")
    return out[:target]


def write_gendered(path: pathlib.Path, male: list[str], female: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = ["gender\tidx\tname\tweight"]
    per = len(male)
    for gender, pool in (("M", male), ("F", female)):
        for i, name in enumerate(pool, start=1):
            w = max(1, 500 - (i * 499 // max(len(pool), 1)))
            lines.append(f"{gender}\t{i}\t{name}\t{w}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_last(path: pathlib.Path, names: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = ["idx\tname\tweight"]
    for i, name in enumerate(names, start=1):
        w = max(1, 500 - (i * 499 // max(len(names), 1)))
        lines.append(f"{i}\t{name}\t{w}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    male_first = _dedupe_pad(MALE_FIRST, 250)
    female_first = _dedupe_pad(FEMALE_FIRST, 250)
    male_middle = _dedupe_pad(MALE_MIDDLE, 75)
    female_middle = _dedupe_pad(FEMALE_MIDDLE, 75)
    last = _fill_last_names(LAST_BASE + LAST_EXTRA, 1000)

    for pool in (male_first, female_first, male_middle, female_middle, last):
        for n in pool:
            if re.search(r"[0-9]", n) or not _single_token(n):
                raise ValueError(f"invalid pool name: {n}")

    write_gendered(OUT / "first_names.tsv", male_first, female_first)
    write_gendered(OUT / "middle_names.tsv", male_middle, female_middle)
    write_last(OUT / "last_names.tsv", last)
    print(
        f"Wrote {len(male_first)}+{len(female_first)} first, "
        f"{len(male_middle)}+{len(female_middle)} middle, {len(last)} last names to {OUT}"
    )


if __name__ == "__main__":
    main()

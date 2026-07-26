INSERT INTO users (student_id, display_name, email)
VALUES ('nmsi-demo', 'Alex Morgan', 'alex.morgan@nmsi.example');

INSERT INTO categories (slug, name, description, keywords) VALUES
('academic-study', 'Academic study and assessments', 'Help with study skills, assessment pressure, extensions and academic progress.', 'assessment deadline extension mitigating circumstances study skills writing statistics academic progress'),
('programme-choices', 'Programme and course choices', 'Help choosing modules, understanding programme rules and planning an academic route.', 'module course programme pathway prerequisites degree requirements tutor transfer'),
('wellbeing', 'Wellbeing and emotional support', 'Confidential support for stress, low mood, anxiety, relationships and difficult experiences.', 'stress anxiety low mood relationship homesick overwhelmed counselling emotional'),
('money-funding', 'Money and funding', 'Advice about fees, emergency funds, budgeting and financial difficulty.', 'money fee tuition hardship emergency fund debt budgeting scholarship'),
('digital-access', 'Digital access and IT', 'Help with accounts, devices, software, connectivity and accessible technology.', 'laptop wifi account password software device printing digital accessibility'),
('disability-access', 'Disability and accessibility', 'Adjustments and access support for disability, long-term conditions and neurodivergence.', 'disability adjustment access dyslexia adhd autism mobility sensory learning support'),
('international-visa', 'International and visa matters', 'Specialist guidance about visas, immigration conditions and studying internationally.', 'visa immigration international student right to study travel document'),
('careers-work', 'Careers and employment', 'Career exploration, applications, placements and employment guidance.', 'career job cv application interview placement internship employability'),
('housing-campus-life', 'Housing and campus life', 'Help with accommodation, flatmates, commuting and settling into campus life.', 'housing accommodation rent flatmate residence commute campus'),
('safety-urgent', 'Safety and urgent support', 'Immediate routes for safety concerns, harassment, crisis or urgent risk.', 'urgent emergency danger safety harassment assault crisis safeguarding');

INSERT INTO facilities (
    slug, name, summary, provider, location, distance_minutes, rating,
    response_time, contact_mode, eligibility, preparation, tags
) VALUES
('academic-skills-hub', 'Academic Skills Hub', 'One-to-one and group support for planning assignments, academic writing, study routines and quantitative skills.', 'Learning Development Team', 'Learning Commons, Level 2', 6, 4.70, 'Usually within 2 working days', 'In person, video or workshop', 'All current NMSI students', 'Bring the assignment brief or a short description of the skill you want to develop.', 'writing statistics study planning assignment feedback'),
('student-casework', 'Student Casework and Extensions', 'Guidance on extensions, mitigating circumstances, academic progress and formal university processes.', 'Student Casework Team', 'Student Services Centre', 8, 4.50, 'Same day triage; decision times vary', 'Online form followed by email or appointment', 'Current students affected by circumstances that influence study', 'Know the affected assessment and deadline; evidence can be discussed after initial contact.', 'extension deadline mitigating circumstances progress appeal'),
('programme-advice', 'Programme Advice Service', 'Help comparing modules, checking programme rules and understanding who owns an academic decision.', 'Faculty Education Office', 'Science Building, Room 1.14', 11, 4.60, 'Within 3 working days', 'Drop-in, email or booked meeting', 'Students registered on an NMSI programme', 'Bring your programme name and the modules or options you are considering.', 'module choice course transfer programme rules prerequisites'),
('wellbeing-centre', 'Student Wellbeing Centre', 'Confidential, non-judgmental support when personal or emotional concerns are affecting university life.', 'Student Wellbeing Team', 'Willow House', 9, 4.80, 'Initial response within 1 working day', 'In person, video or telephone', 'All current NMSI students', 'No diagnosis or documents are required; describe what has been happening in your own words.', 'stress anxiety low mood relationships homesick counselling confidential'),
('listening-line', 'Evening Listening Line', 'A confidential listening service for students who want to talk outside normal office hours.', 'Trained Student Listeners with staff supervision', 'Telephone and secure chat', 0, 4.40, 'Open 18:00–00:00 daily', 'Telephone or secure chat', 'All NMSI students', 'No preparation is needed.', 'evening talk loneliness worry immediate listening'),
('fees-funding', 'Fees and Funding Advice', 'Specialist guidance about tuition fees, hardship funding, budgeting and urgent financial pressure.', 'Student Finance Advice Team', 'Student Services Centre', 8, 4.55, 'Within 2 working days', 'In person, telephone or secure form', 'Current and prospective NMSI students', 'If available, bring a rough monthly budget and any relevant funding letter.', 'fees hardship money budget debt scholarship emergency fund'),
('digital-access-desk', 'Digital Access Desk', 'Practical help with portal accounts, university devices, software, connectivity and assistive technology setup.', 'Digital Education and IT', 'Library Entrance Desk', 4, 4.35, 'Walk-in queue or same-day callback', 'Walk-in, telephone or remote screen share', 'NMSI students and staff', 'Bring the device and note any error message. Never share a password.', 'wifi password laptop software account device assistive technology'),
('accessibility-service', 'Disability and Accessibility Service', 'Confidential advice about reasonable adjustments, disability-related study support and accessible learning.', 'Accessibility Advisers', 'Willow House, Ground Floor', 9, 4.75, 'Initial response within 3 working days', 'In person, video, telephone or email', 'Students with a disability, long-term condition or possible access need', 'Evidence is not required for an initial conversation. Explain the barrier you are experiencing.', 'adjustment dyslexia adhd autism mobility sensory disability long-term condition'),
('international-advice', 'International Student Advice', 'Regulated advice about visas, immigration conditions, travel and right-to-study questions.', 'International Student Advisers', 'Global Centre, Room G04', 14, 4.65, 'Urgent visa triage the same day', 'In person, video or secure form', 'International applicants and current students', 'Bring your passport and visa information only when requested through a secure route.', 'visa immigration travel right to study international'),
('careers-studio', 'Careers Studio', 'Support with career direction, CVs, applications, interviews, placements and graduate work.', 'Careers and Employability Service', 'Innovation Building, Level 1', 12, 4.60, 'Drop-in daily; appointments within 4 days', 'Drop-in, workshop or booked meeting', 'NMSI students and recent graduates', 'Bring a role description or draft document if you want application feedback.', 'career cv job interview placement internship application'),
('accommodation-support', 'Accommodation and Campus Living', 'Help with university accommodation, tenancy questions, flatmate issues and settling into campus life.', 'Campus Living Team', 'Residence Services, West Court', 17, 4.20, 'Within 2 working days', 'In person, telephone or email', 'Students living in or applying for NMSI accommodation', 'Bring the residence or tenancy details that relate to your question.', 'housing rent flatmate residence tenancy commute'),
('campus-safety', 'Campus Safety and Urgent Support', 'Immediate support for safety concerns on campus, including urgent welfare and safeguarding routes.', 'Campus Safety Team', 'Security Centre beside North Gate', 5, 4.50, '24 hours a day', 'Emergency telephone, in person or security point', 'Anyone on NMSI premises', 'If there is immediate danger, call local emergency services first.', 'urgent danger safety harassment assault safeguarding emergency');

INSERT INTO facility_categories (facility_id, category_id)
SELECT f.id, c.id FROM facilities f, categories c
WHERE
    (f.slug = 'academic-skills-hub' AND c.slug = 'academic-study')
 OR (f.slug = 'student-casework' AND c.slug IN ('academic-study', 'wellbeing'))
 OR (f.slug = 'programme-advice' AND c.slug IN ('programme-choices', 'academic-study'))
 OR (f.slug = 'wellbeing-centre' AND c.slug IN ('wellbeing', 'disability-access'))
 OR (f.slug = 'listening-line' AND c.slug IN ('wellbeing', 'safety-urgent'))
 OR (f.slug = 'fees-funding' AND c.slug IN ('money-funding', 'international-visa'))
 OR (f.slug = 'digital-access-desk' AND c.slug IN ('digital-access', 'disability-access'))
 OR (f.slug = 'accessibility-service' AND c.slug IN ('disability-access', 'academic-study', 'wellbeing'))
 OR (f.slug = 'international-advice' AND c.slug IN ('international-visa', 'money-funding', 'housing-campus-life'))
 OR (f.slug = 'careers-studio' AND c.slug IN ('careers-work', 'programme-choices'))
 OR (f.slug = 'accommodation-support' AND c.slug IN ('housing-campus-life', 'money-funding', 'wellbeing'))
 OR (f.slug = 'campus-safety' AND c.slug IN ('safety-urgent', 'wellbeing', 'housing-campus-life'));


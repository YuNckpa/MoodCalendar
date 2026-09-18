-- Add email on profiles for friend search (fuzzy by email / nickname / friend_code)
-- Run after 002_avatars_and_defaults.sql

alter table public.profiles
  add column if not exists email text;

create index if not exists profiles_email_idx on public.profiles (email);
create index if not exists profiles_display_name_idx on public.profiles (display_name);

-- Backfill from auth.users
update public.profiles p
set email = u.email
from auth.users u
where p.id = u.id
  and (p.email is null or p.email = '');

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_name text;
  v_avatar text;
begin
  v_name := coalesce(new.raw_user_meta_data->>'display_name', split_part(new.email, '@', 1), '用户');
  v_avatar := 'https://api.dicebear.com/7.x/thumbs/png?seed=' || new.id::text || '&size=128';
  insert into public.profiles (id, display_name, avatar_url, friend_code, email)
  values (
    new.id,
    v_name,
    v_avatar,
    public.gen_friend_code(),
    new.email
  )
  on conflict (id) do update
    set email = excluded.email;
  insert into public.user_settings (user_id, theme_style, custom_moods_json, updated_at)
  values (new.id, 'MALE_BLUE', '[]', now())
  on conflict (user_id) do nothing;
  return new;
end;
$$;

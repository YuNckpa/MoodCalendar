-- MoodCalendar Phase 2: Auth profiles, sync tables, friends, interactions
-- Run in Supabase SQL Editor (or via CLI). Enable Email auth in Dashboard.

create extension if not exists "pgcrypto";

-- Profiles
create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text not null default '',
  avatar_url text,
  friend_code text not null unique,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Settings (before new-user trigger)
create table if not exists public.user_settings (
  user_id uuid primary key references auth.users (id) on delete cascade,
  theme_style text not null default 'MALE_BLUE',
  custom_moods_json text not null default '[]',
  updated_at timestamptz not null default now()
);

create or replace function public.gen_friend_code()
returns text language sql as $$
  select upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
$$;

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, display_name, friend_code)
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'display_name', split_part(new.email, '@', 1), '用户'),
    public.gen_friend_code()
  );
  insert into public.user_settings (user_id, theme_style, custom_moods_json, updated_at)
  values (new.id, 'MALE_BLUE', '[]', now())
  on conflict (user_id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Calendar events (cloud mirror)
create table if not exists public.calendar_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  client_id bigint,
  type text not null,
  title text not null,
  note text not null default '',
  target_date text not null,
  all_day boolean not null default true,
  recurrence text not null default 'YEARLY',
  remind_on_day boolean not null default true,
  remind_time text not null default '09:00',
  advance_reminders_json text not null default '[]',
  display_mode text not null default 'DAYS_ONLY',
  yearly_mode text not null default 'SAME_DAY',
  yearly_dates_json text not null default '[]',
  weekly_days_json text not null default '[]',
  monthly_days_json text not null default '[]',
  background_image_uri text,
  calendar_system text not null default 'SOLAR',
  lunar_year int not null default 0,
  lunar_month int not null default 0,
  lunar_day int not null default 0,
  sort_order int not null default 0,
  archived boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create unique index if not exists calendar_events_user_sync on public.calendar_events (user_id, id);
create index if not exists calendar_events_user_updated on public.calendar_events (user_id, updated_at);

-- Mood entries
create table if not exists public.mood_entries (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  client_id bigint,
  date text not null,
  text text not null default '',
  emoji text not null default '😊',
  emoji_label text not null default '',
  visibility text not null default 'FRIENDS_ALL',
  is_period boolean not null default false,
  group_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists mood_entries_user_updated on public.mood_entries (user_id, updated_at);
create index if not exists mood_entries_user_date on public.mood_entries (user_id, date);

-- Mood images
create table if not exists public.mood_images (
  id uuid primary key default gen_random_uuid(),
  mood_id uuid not null references public.mood_entries (id) on delete cascade,
  remote_path text not null,
  sort_order int not null default 0
);

create index if not exists mood_images_mood on public.mood_images (mood_id);

-- Friendships
create table if not exists public.friendships (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references auth.users (id) on delete cascade,
  addressee_id uuid not null references auth.users (id) on delete cascade,
  status text not null check (status in ('pending', 'accepted', 'blocked')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (requester_id, addressee_id),
  check (requester_id <> addressee_id)
);

create index if not exists friendships_addressee on public.friendships (addressee_id, status);

-- Friend groups
create table if not exists public.friend_groups (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users (id) on delete cascade,
  name text not null,
  created_at timestamptz not null default now()
);

create table if not exists public.friend_group_members (
  group_id uuid not null references public.friend_groups (id) on delete cascade,
  member_id uuid not null references auth.users (id) on delete cascade,
  primary key (group_id, member_id)
);

alter table public.mood_entries
  drop constraint if exists mood_entries_group_id_fkey;
alter table public.mood_entries
  add constraint mood_entries_group_id_fkey
  foreign key (group_id) references public.friend_groups (id) on delete set null;

-- Interactions
create table if not exists public.mood_likes (
  mood_id uuid not null references public.mood_entries (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (mood_id, user_id)
);

create table if not exists public.mood_comments (
  id uuid primary key default gen_random_uuid(),
  mood_id uuid not null references public.mood_entries (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  text text not null check (char_length(text) between 1 and 500),
  created_at timestamptz not null default now()
);

create table if not exists public.mood_favorites (
  mood_id uuid not null references public.mood_entries (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (mood_id, user_id)
);

-- Helpers
create or replace function public.are_friends(a uuid, b uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.friendships f
    where f.status = 'accepted'
      and (
        (f.requester_id = a and f.addressee_id = b)
        or (f.requester_id = b and f.addressee_id = a)
      )
  );
$$;

create or replace function public.can_read_mood(mood public.mood_entries)
returns boolean language sql stable security definer set search_path = public as $$
  select
    mood.deleted_at is null
    and (
      mood.user_id = auth.uid()
      or (
        mood.visibility = 'FRIENDS_ALL'
        and public.are_friends(auth.uid(), mood.user_id)
      )
      or (
        mood.visibility = 'GROUP'
        and mood.group_id is not null
        and exists (
          select 1 from public.friend_group_members m
          where m.group_id = mood.group_id and m.member_id = auth.uid()
        )
      )
    );
$$;

-- RLS
alter table public.profiles enable row level security;
alter table public.user_settings enable row level security;
alter table public.calendar_events enable row level security;
alter table public.mood_entries enable row level security;
alter table public.mood_images enable row level security;
alter table public.friendships enable row level security;
alter table public.friend_groups enable row level security;
alter table public.friend_group_members enable row level security;
alter table public.mood_likes enable row level security;
alter table public.mood_comments enable row level security;
alter table public.mood_favorites enable row level security;

-- profiles policies
create policy profiles_select on public.profiles for select to authenticated
  using (true);
create policy profiles_update on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

-- settings
create policy settings_all on public.user_settings for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- calendar events (owner only)
create policy events_all on public.calendar_events for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());

-- moods
create policy moods_select on public.mood_entries for select to authenticated
  using (public.can_read_mood(mood_entries));
create policy moods_insert on public.mood_entries for insert to authenticated
  with check (user_id = auth.uid());
create policy moods_update on public.mood_entries for update to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy moods_delete on public.mood_entries for delete to authenticated
  using (user_id = auth.uid());

-- mood images
create policy mood_images_select on public.mood_images for select to authenticated
  using (
    exists (
      select 1 from public.mood_entries e
      where e.id = mood_images.mood_id and public.can_read_mood(e)
    )
  );
create policy mood_images_write on public.mood_images for all to authenticated
  using (
    exists (
      select 1 from public.mood_entries e
      where e.id = mood_images.mood_id and e.user_id = auth.uid()
    )
  )
  with check (
    exists (
      select 1 from public.mood_entries e
      where e.id = mood_images.mood_id and e.user_id = auth.uid()
    )
  );

-- friendships
create policy friendships_select on public.friendships for select to authenticated
  using (requester_id = auth.uid() or addressee_id = auth.uid());
create policy friendships_insert on public.friendships for insert to authenticated
  with check (requester_id = auth.uid());
create policy friendships_update on public.friendships for update to authenticated
  using (requester_id = auth.uid() or addressee_id = auth.uid());
create policy friendships_delete on public.friendships for delete to authenticated
  using (requester_id = auth.uid() or addressee_id = auth.uid());

-- groups
create policy groups_all on public.friend_groups for all to authenticated
  using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy group_members_select on public.friend_group_members for select to authenticated
  using (
    member_id = auth.uid()
    or exists (
      select 1 from public.friend_groups g
      where g.id = group_id and g.owner_id = auth.uid()
    )
  );
create policy group_members_write on public.friend_group_members for all to authenticated
  using (
    exists (
      select 1 from public.friend_groups g
      where g.id = group_id and g.owner_id = auth.uid()
    )
  )
  with check (
    exists (
      select 1 from public.friend_groups g
      where g.id = group_id and g.owner_id = auth.uid()
    )
  );

-- likes / comments / favorites
create policy likes_select on public.mood_likes for select to authenticated
  using (
    exists (select 1 from public.mood_entries e where e.id = mood_id and public.can_read_mood(e))
  );
create policy likes_insert on public.mood_likes for insert to authenticated
  with check (
    user_id = auth.uid()
    and exists (select 1 from public.mood_entries e where e.id = mood_id and public.can_read_mood(e))
  );
create policy likes_delete on public.mood_likes for delete to authenticated
  using (user_id = auth.uid());

create policy comments_select on public.mood_comments for select to authenticated
  using (
    exists (select 1 from public.mood_entries e where e.id = mood_id and public.can_read_mood(e))
  );
create policy comments_insert on public.mood_comments for insert to authenticated
  with check (
    user_id = auth.uid()
    and exists (select 1 from public.mood_entries e where e.id = mood_id and public.can_read_mood(e))
  );
create policy comments_delete on public.mood_comments for delete to authenticated
  using (user_id = auth.uid());

create policy favorites_select on public.mood_favorites for select to authenticated
  using (user_id = auth.uid());
create policy favorites_insert on public.mood_favorites for insert to authenticated
  with check (
    user_id = auth.uid()
    and exists (select 1 from public.mood_entries e where e.id = mood_id and public.can_read_mood(e))
  );
create policy favorites_delete on public.mood_favorites for delete to authenticated
  using (user_id = auth.uid());

-- Storage bucket (also create in Dashboard if needed)
insert into storage.buckets (id, name, public)
values ('mood-images', 'mood-images', true)
on conflict (id) do nothing;

create policy mood_images_storage_read on storage.objects for select to authenticated
  using (bucket_id = 'mood-images');
create policy mood_images_storage_write on storage.objects for insert to authenticated
  with check (bucket_id = 'mood-images' and (storage.foldername(name))[1] = auth.uid()::text);
create policy mood_images_storage_update on storage.objects for update to authenticated
  using (bucket_id = 'mood-images' and (storage.foldername(name))[1] = auth.uid()::text);
create policy mood_images_storage_delete on storage.objects for delete to authenticated
  using (bucket_id = 'mood-images' and (storage.foldername(name))[1] = auth.uid()::text);
